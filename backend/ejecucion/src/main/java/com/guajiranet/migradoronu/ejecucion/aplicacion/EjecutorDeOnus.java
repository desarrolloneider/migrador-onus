package com.guajiranet.migradoronu.ejecucion.aplicacion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import com.guajiranet.migradoronu.comun.puertos.ActorType;
import com.guajiranet.migradoronu.comun.puertos.AuditAction;
import com.guajiranet.migradoronu.comun.puertos.AuditEntry;
import com.guajiranet.migradoronu.comun.puertos.AuditTrailPort;
import com.guajiranet.migradoronu.ejecucion.aplicacion.CambioDePerfilUnaOnu.Contexto;
import com.guajiranet.migradoronu.ejecucion.aplicacion.CambioDePerfilUnaOnu.Final;
import com.guajiranet.migradoronu.ejecucion.aplicacion.CambioDePerfilUnaOnu.Objetivo;
import com.guajiranet.migradoronu.ejecucion.aplicacion.CambioDePerfilUnaOnu.Resultado;
import com.guajiranet.migradoronu.ejecucion.puertos.salida.ColaDeEjecucionPort;
import com.guajiranet.migradoronu.ejecucion.puertos.salida.OltConsoleFactoryPort;
import com.guajiranet.migradoronu.ejecucion.puertos.salida.OltConsolePort;
import com.guajiranet.migradoronu.procedimientos.dominio.RespuestaHuawei;

/**
 * El proceso ejecutor (arquitectura 9.1): toma la siguiente ONU confirmada de una OLT, le cambia el perfil (o lo
 * simula) y deja el resultado. Una sesión por OLT (RN-34): cada OLT la atiende un solo hilo, y varias OLT pueden ir a
 * la vez. La sesión se reutiliza entre ONUs de la misma OLT y la configuración se guarda cada N ONUs cambiadas (el
 * {@code save} es de toda la OLT y es lo que más tarda). Las ONUs fuera de línea no se tocan.
 */
public class EjecutorDeOnus {

    private final ColaDeEjecucionPort cola;
    private final OltConsoleFactoryPort consolas;
    private final CambioDePerfilUnaOnu cambio;
    private final String perfilOrigen;
    private final AuditTrailPort auditoria;
    private final CambioDeGestionUnaOnu gestion;
    private final int vlanGestion;
    private CambioDeServicioWanUnaOnu servicio;
    /** Cuántas ONUs de servicio WAN (paso 3) van a la vez en una OLT. 1 = de a una (decisión del 15 de octubre). */
    private volatile int servicioEnParalelo = 1;
    /** Cuántas ONUs de VLAN de gestión (paso 1) van a la vez en una OLT. 1: con 9 a la vez SmartOLT cortó conexiones y la OLT no procesó (LOT-2026-000129). */
    private volatile int gestionEnParalelo = 1;

    public void servicioEnParalelo(int n) {
        this.servicioEnParalelo = Math.max(1, n);
    }

    public void gestionEnParalelo(int n) {
        this.gestionEnParalelo = Math.max(1, n);
    }

    /** Si al terminar bien el paso 3 se reinicia la ONU desde la OLT. */
    public void reiniciarAlTerminar(boolean reiniciar) {
        if (servicio != null) {
            servicio.reiniciarAlTerminar(reiniciar);
        }
    }

    /** El cambio de servicio WAN (GenieACS); sin él, los lotes de ese tipo fallan con un mensaje claro. */
    public EjecutorDeOnus conServicioWan(CambioDeServicioWanUnaOnu cambio) {
        this.servicio = cambio;
        return this;
    }

    public EjecutorDeOnus(ColaDeEjecucionPort cola, OltConsoleFactoryPort consolas, CambioDePerfilUnaOnu cambio, String perfilOrigen,
                          AuditTrailPort auditoria, CambioDeGestionUnaOnu gestion, int vlanGestion) {
        this.gestion = Objects.requireNonNull(gestion, "gestion");
        this.vlanGestion = vlanGestion;
        this.cola = Objects.requireNonNull(cola, "cola");
        this.consolas = Objects.requireNonNull(consolas, "consolas");
        this.cambio = Objects.requireNonNull(cambio, "cambio");
        this.perfilOrigen = Objects.requireNonNull(perfilOrigen, "perfilOrigen");
        this.auditoria = Objects.requireNonNull(auditoria, "auditoria");
    }

    /** Una sesión quieta más de esto se cierra y se abre otra antes de usarla. */
    static final long QUIETA_MAXIMO_MS = 120_000;

    /** La sesión de un hilo con una OLT y las ONUs ya cambiadas que esperan el próximo save. */
    public final class SesionOlt {
        private final String olt;
        private OltConsolePort real;
        private final List<Long> sinGuardar = new ArrayList<>();
        /** Las ONUs de servicio WAN que van en paralelo en esta OLT. */
        private final java.util.concurrent.Semaphore cupos;
        private final int maximo;
        /** Las ONUs de VLAN de gestión que van en paralelo en esta OLT. */
        private final java.util.concurrent.Semaphore cuposGestion;
        private final int maximoGestion;

        private SesionOlt(String olt, int maximo, int maximoGestion) {
            this.olt = olt;
            this.maximo = maximo;
            this.cupos = new java.util.concurrent.Semaphore(maximo);
            this.maximoGestion = maximoGestion;
            this.cuposGestion = new java.util.concurrent.Semaphore(maximoGestion);
        }

        /** Espera a que terminen las ONUs que van en paralelo. */
        private void esperarParalelas() {
            try {
                cupos.acquire(maximo);
                cupos.release(maximo);
                cuposGestion.acquire(maximoGestion);
                cuposGestion.release(maximoGestion);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        public String olt() {
            return olt;
        }

        /** La consola compartida por las ONUs de esta OLT (ver {@link ConsolaCompartida}): una por sesión. */
        private ConsolaCompartida compartida;

        /** Cuándo se usó la consola por última vez: la OLT cierra las sesiones quietas (el servicio WAN espera minutos). */
        private long ultimoUso;

        private OltConsolePort compartida() {
            long ahora = System.currentTimeMillis();
            if (real != null && ahora - ultimoUso > QUIETA_MAXIMO_MS) {
                descartar();
            }
            ultimoUso = ahora;
            if (real == null) {
                real = consolas.abrir(olt);
                compartida = new ConsolaCompartida(real);
            }
            return compartida;
        }

        private void descartar() {
            if (real != null) {
                try {
                    real.close();
                } catch (RuntimeException ignorada) {
                    // Ya estaba caída.
                }
                real = null;
                compartida = null;
            }
        }
    }

    public SesionOlt nuevaSesion(String olt) {
        return new SesionOlt(olt, servicioEnParalelo, gestionEnParalelo);
    }

    /**
     * Ejecuta la siguiente ONU de esa OLT si hay alguna esperando. Devuelve true si hizo algo.
     *
     * @param onusPorGuardado cada cuántas ONUs cambiadas se guarda la configuración (1 = después de cada una)
     */
    public boolean ejecutarSiguiente(long procesoId, SesionOlt s, int onusPorGuardado) {
        Optional<TrabajoOnu> tomado = cola.tomarSiguiente(procesoId, s.olt());
        if (tomado.isEmpty()) {
            return false;
        }
        TrabajoOnu t = tomado.get();
        if (t.esTresPasos()) {
            // En el hilo de la OLT: la siguiente ONU de esta OLT no se toma hasta que esta termine los 3 pasos.
            ejecutarTresPasos(t, s);
            synchronized (s) {
                if (s.sinGuardar.size() >= onusPorGuardado) {
                    guardar(s, t.loteId());
                }
            }
            return true;
        }
        if (t.esVlanGestion()) {
            ejecutarGestionEnParalelo(t, s);
            return true;
        }
        if (t.esServicioWan()) {
            // Con muchas ONUs en paralelo se guarda la OLT de vez en cuando, no solo al final.
            synchronized (s) {
                if (s.sinGuardar.size() >= Math.max(onusPorGuardado, s.maximo)) {
                    guardar(s, t.loteId());
                }
            }
            ejecutarServicioEnParalelo(t, s);
            return true;
        }
        // La consola es de a una ONU: si hay ONUs de servicio WAN en paralelo en esta OLT, esperan su turno.
        synchronized (s) {
            return ejecutarPerfil(t, s, onusPorGuardado);
        }
    }

    private boolean ejecutarPerfil(TrabajoOnu t, SesionOlt s, int onusPorGuardado) {
        boolean enGrupo = onusPorGuardado > 1 && t.guardar();
        try {
            String[] tarjeta = t.tarjeta().split("/");
            Objetivo objetivo = new Objetivo(t.oltCodigo(), Integer.parseInt(tarjeta[0].strip()), Integer.parseInt(tarjeta[1].strip()),
                    t.pon(), t.onuId(), t.perfilesOrigen() == null ? List.of(perfilOrigen, "SmartOLT", CambioDePerfilUnaOnu.SIN_PERFIL) : t.perfilesOrigen(),
                    t.perfilDestino(), t.guardar() && !enGrupo, false, t.sn(),
                    gestion == null ? null : () -> gestion.tieneGestionEn(t.sn(), vlanGestion));
            Contexto ctx = new Contexto(ActorType.PROCESO_EJECUTOR, t.confirmadoPor(), t.loteId(), t.ejecucionId(),
                    paso -> cola.avanzarPaso(t.ejecucionId(), paso));
            prepararServicioWan(t, ctx);
            Resultado r = cambio.ejecutar(objetivo, !t.simulacion(), ctx, s::compartida);
            switch (r.fin()) {
                case COMPLETADA -> {
                    cola.terminar(t.ejecucionId(), "COMPLETADA", r.paso(), null, r.mensaje());
                    if (enGrupo) {
                        s.sinGuardar.add(t.ejecucionId());
                    }
                }
                case SIMULADA -> cola.terminar(t.ejecucionId(), "COMPLETADA", r.paso(), null, r.mensaje());
                case YA_TENIA, EXCLUIDA -> cola.terminar(t.ejecucionId(), "EXCLUIDA_AL_EJECUTAR", r.paso(), r.codigoFallo(), r.mensaje());
                case FALLIDA -> {
                    cola.terminar(t.ejecucionId(), "FALLIDA", r.paso(), r.codigoFallo(), r.mensaje());
                    // Tras una falla la sesión puede haber quedado en cualquier modo o caída: se guarda lo pendiente y se abre otra.
                    guardar(s, t.loteId());
                    s.descartar();
                }
            }
        } catch (RuntimeException e) {
            // Nunca queda una ONU en curso para siempre: se cierra como fallida con el motivo.
            cola.terminar(t.ejecucionId(), "FALLIDA", "P01", "FALLO_SISTEMA_NO_RESPONDE", "El ejecutor no pudo procesar la ONU: " + e.getMessage());
            guardar(s, t.loteId());
            s.descartar();
        }
        if (s.sinGuardar.size() >= onusPorGuardado) {
            guardar(s, t.loteId());
        }
        return true;
    }

    /**
     * Lote "Los 3 pasos": VLAN 46, GUAJIRANET1 y servicio WAN seguidos con una ONU. Así nunca hay dos ONUs de la misma OLT
     * con la VLAN 46 a la vez (el DHCP de OLT_FONSECA le daba la misma IP a varias y solo una llegaba a GenieACS, 16 de
     * octubre). Si falla el paso 1 o el 2, queda sin VLAN 46 ni TR-069 y con el perfil destino.
     */
    private void ejecutarTresPasos(TrabajoOnu t, SesionOlt s) {
        try {
            if (servicio == null) {
                throw new IllegalStateException("El ejecutor no tiene configurado GenieACS.");
            }
            Contexto ctx = new Contexto(ActorType.PROCESO_EJECUTOR, t.confirmadoPor(), t.loteId(), t.ejecucionId(),
                    paso -> cola.avanzarPaso(t.ejecucionId(), paso));
            boolean real = !t.simulacion();
            // Antes de tocar nada: las que no van a poder terminar quedan fuera sin cambios.
            cola.avanzarPaso(t.ejecucionId(), "P01");
            CambioDeServicioWanUnaOnu.Antes antes = servicio.revisarAntes(t);
            if (!antes.enGenie()) {
                cola.terminar(t.ejecucionId(), "EXCLUIDA_AL_EJECUTAR", "P01", "EXC_NO_EN_GENIEACS",
                        "La ONU no está registrada en GenieACS: no se tocó (sin GenieACS no se le puede hacer el paso 3).");
                return;
            }
            // SmartOLT a veces la muestra en línea cuando ya se apagó (479DC7B3, 16 de octubre): manda la OLT.
            Optional<Boolean> enLinea = enLineaEnOlt(t, s);
            if (enLinea.isPresent() && !enLinea.get()) {
                cola.terminar(t.ejecucionId(), "EXCLUIDA_AL_EJECUTAR", "P01", "EXC_FUERA_DE_LINEA",
                        "La ONU está fuera de línea en la OLT: no se tocó.");
                return;
            }
            if (antes.reportaPorInternet() != null) {
                ejecutarYaPorInternet(t, s, ctx, antes.reportaPorInternet());
                return;
            }
            prepararServicioWan(t, ctx);
            Resultado r1 = gestion.ejecutar(t, vlanGestion, real, ctx);
            if (r1.fin() == Final.EXCLUIDA) {
                // No se tocó nada (por ejemplo, está fuera de línea).
                cola.terminar(t.ejecucionId(), "EXCLUIDA_AL_EJECUTAR", r1.paso(), r1.codigoFallo(), "Paso 1 (VLAN 46): " + r1.mensaje());
                return;
            }
            if (r1.fin() == Final.FALLIDA) {
                terminarLimpia(t, s, ctx, r1, "Paso 1 (VLAN 46)");
                return;
            }
            String[] tarjeta = t.tarjeta().split("/");
            // El save de la OLT lo hace el paso 3 (que vuelve a poner el perfil), con las demás ONUs.
            Objetivo objetivo = new Objetivo(t.oltCodigo(), Integer.parseInt(tarjeta[0].strip()), Integer.parseInt(tarjeta[1].strip()),
                    t.pon(), t.onuId(), t.perfilesOrigen() == null ? List.of(perfilOrigen, "SmartOLT", CambioDePerfilUnaOnu.SIN_PERFIL) : t.perfilesOrigen(),
                    t.perfilDestino(), false, false, t.sn(), () -> gestion.tieneGestionEn(t.sn(), vlanGestion));
            Resultado r2;
            synchronized (s) {
                r2 = cambio.ejecutar(objetivo, real, ctx, s::compartida);
                if (r2.fin() == Final.FALLIDA) {
                    s.descartar();
                }
            }
            if (r2.fin() == Final.FALLIDA || r2.fin() == Final.EXCLUIDA) {
                terminarLimpia(t, s, ctx, r2, "Paso 2 (" + t.perfilDestino() + ")");
                return;
            }
            // Paso 3: deja el resultado final de la ONU (y la suma al próximo save de la OLT).
            ejecutarServicio(t, s);
        } catch (RuntimeException e) {
            cola.terminar(t.ejecucionId(), "FALLIDA", "P01", "FALLO_SISTEMA_NO_RESPONDE", "El ejecutor no pudo procesar la ONU: " + e.getMessage());
        }
    }

    /** ¿Está en línea según la OLT (display ont info)? Vacío si no se pudo leer: entonces se sigue y lo dice cada paso. */
    private Optional<Boolean> enLineaEnOlt(TrabajoOnu t, SesionOlt s) {
        String[] tarjeta = t.tarjeta().split("/");
        synchronized (s) {
            try {
                OltConsolePort c = s.compartida();
                if (c.prompt().contains("-if-")) {
                    c.ejecutar("quit");
                }
                c.ejecutar("enable");
                String foto = c.ejecutar("display ont info " + tarjeta[0].strip() + " " + tarjeta[1].strip() + " " + t.pon() + " " + t.onuId());
                return com.guajiranet.migradoronu.procedimientos.dominio.LecturaOntHuawei.leer(foto)
                        .map(com.guajiranet.migradoronu.procedimientos.dominio.LecturaOntHuawei::enLinea);
            } catch (RuntimeException e) {
                s.descartar();
                return Optional.empty();
            }
        }
    }

    /**
     * La ONU ya hace TR-069 por su IP de internet (por ejemplo, EG8145V5 configuradas así desde antes): no necesita VLAN 46
     * ni cambio de servicio. Solo se le asegura GUAJIRANET1 en la OLT, que es lo que la deja apuntando a GenieACS si se resetea.
     */
    private void ejecutarYaPorInternet(TrabajoOnu t, SesionOlt s, Contexto ctx, String ip) {
        String[] tarjeta = t.tarjeta().split("/");
        Objetivo objetivo = new Objetivo(t.oltCodigo(), Integer.parseInt(tarjeta[0].strip()), Integer.parseInt(tarjeta[1].strip()),
                t.pon(), t.onuId(), t.perfilesOrigen() == null ? List.of(perfilOrigen, "SmartOLT", CambioDePerfilUnaOnu.SIN_PERFIL) : t.perfilesOrigen(),
                t.perfilDestino(), false, false, t.sn(), null);
        Resultado r;
        synchronized (s) {
            r = cambio.ejecutar(objetivo, !t.simulacion(), ctx, s::compartida);
            if (r.fin() == Final.FALLIDA) {
                s.descartar();
            }
        }
        String inicio = "Ya se reportaba a GenieACS por internet (" + ip + "): no necesitó VLAN 46 ni cambio de servicio. ";
        switch (r.fin()) {
            case COMPLETADA, YA_TENIA, SIMULADA -> {
                cola.terminar(t.ejecucionId(), "COMPLETADA", r.paso(), null, inicio
                        + (r.fin() == Final.COMPLETADA ? "Se le puso " + t.perfilDestino() + "." : r.fin() == Final.YA_TENIA
                        ? "Ya tenía " + t.perfilDestino() + "." : r.mensaje()));
                if (r.fin() == Final.COMPLETADA && r.real() && t.guardar()) {
                    synchronized (s) {
                        s.sinGuardar.add(t.ejecucionId());
                    }
                }
            }
            case EXCLUIDA -> cola.terminar(t.ejecucionId(), "EXCLUIDA_AL_EJECUTAR", r.paso(), r.codigoFallo(), inicio + r.mensaje());
            case FALLIDA -> cola.terminar(t.ejecucionId(), "FALLIDA", r.paso(), r.codigoFallo() == null ? "FALLO_OLT_RECHAZO" : r.codigoFallo(),
                    inicio + "No se pudo poner " + t.perfilDestino() + ": " + r.mensaje());
        }
    }

    /** Falló el paso 1 o el 2 de "Los 3 pasos": se deja limpia (sin VLAN 46 ni TR-069, con el perfil destino) y fallida. */
    private void terminarLimpia(TrabajoOnu t, SesionOlt s, Contexto ctx, Resultado r, String nombre) {
        String codigo = r.codigoFallo() == null ? "FALLO_OLT_RECHAZO" : r.codigoFallo();
        Resultado limpia = servicio.fallarYDejarLimpia(t, ctx, s::compartida, s, r.paso() == null ? "P03" : r.paso(), codigo,
                nombre + ": " + r.mensaje());
        cola.terminar(t.ejecucionId(), "FALLIDA", limpia.paso(), codigo, limpia.mensaje());
    }

    /**
     * Pasos 1 y 2 (reales): la etiqueta de GenieACS va antes del cambio, para que la ONU salga con el servicio WAN
     * cambiado la primera vez que se reporte.
     */
    private void prepararServicioWan(TrabajoOnu t, Contexto ctx) {
        if (servicio != null && !t.simulacion()) {
            servicio.prepararEnGenie(t, ctx);
        }
    }

    /** Paso 1 en paralelo: toma un cupo (espera si ya van todos) y la ONU sigue en su propio hilo. */
    private void ejecutarGestionEnParalelo(TrabajoOnu t, SesionOlt s) {
        try {
            s.cuposGestion.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cola.terminar(t.ejecucionId(), "FALLIDA", "P02", "FALLO_SISTEMA_NO_RESPONDE", "El ejecutor se detuvo antes de empezar la ONU.");
            return;
        }
        Thread.ofVirtual().name("gestion-" + t.sn()).start(() -> {
            try {
                ejecutarGestion(t);
            } finally {
                s.cuposGestion.release();
            }
        });
    }

    /** Lote de VLAN de gestión: el cambio va por SmartOLT; no usa la consola de la OLT ni el save. */
    private void ejecutarGestion(TrabajoOnu t) {
        try {
            Contexto ctx = new Contexto(ActorType.PROCESO_EJECUTOR, t.confirmadoPor(), t.loteId(), t.ejecucionId(),
                    paso -> cola.avanzarPaso(t.ejecucionId(), paso));
            prepararServicioWan(t, ctx);
            Resultado r = gestion.ejecutar(t, vlanGestion, !t.simulacion(), ctx);
            switch (r.fin()) {
                case COMPLETADA, SIMULADA -> cola.terminar(t.ejecucionId(), "COMPLETADA", r.paso(), null, r.mensaje());
                case YA_TENIA, EXCLUIDA -> cola.terminar(t.ejecucionId(), "EXCLUIDA_AL_EJECUTAR", r.paso(), r.codigoFallo(), r.mensaje());
                case FALLIDA -> cola.terminar(t.ejecucionId(), "FALLIDA", r.paso(), r.codigoFallo(), r.mensaje());
            }
        } catch (RuntimeException e) {
            cola.terminar(t.ejecucionId(), "FALLIDA", "P02", "FALLO_SISTEMA_NO_RESPONDE", "El ejecutor no pudo procesar la ONU: " + e.getMessage());
        }
    }

    /**
     * Servicio WAN en paralelo: toma un cupo (espera si ya van todos) y la ONU sigue en su propio hilo. Lo que tarda es
     * esperar a GenieACS; la consola de la OLT (P08) se usa de a una ONU, bloqueando la sesión.
     */
    private void ejecutarServicioEnParalelo(TrabajoOnu t, SesionOlt s) {
        try {
            s.cupos.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cola.terminar(t.ejecucionId(), "FALLIDA", "P02", "FALLO_SISTEMA_NO_RESPONDE", "El ejecutor se detuvo antes de empezar la ONU.");
            return;
        }
        Thread.ofVirtual().name("servicio-" + t.sn()).start(() -> {
            try {
                ejecutarServicio(t, s);
            } finally {
                s.cupos.release();
            }
        });
    }

    /** Lote de servicio WAN: GenieACS, SmartOLT y al final el perfil en la OLT (con la sesión de esta OLT). */
    private void ejecutarServicio(TrabajoOnu t, SesionOlt s) {
        try {
            if (servicio == null) {
                throw new IllegalStateException("El ejecutor no tiene configurado GenieACS.");
            }
            Contexto ctx = new Contexto(ActorType.PROCESO_EJECUTOR, t.confirmadoPor(), t.loteId(), t.ejecucionId(),
                    paso -> cola.avanzarPaso(t.ejecucionId(), paso));
            boolean diferir = t.guardar();
            Resultado r = servicio.ejecutar(t, !t.simulacion(), ctx, s::compartida, s, diferir);
            switch (r.fin()) {
                case COMPLETADA, SIMULADA -> {
                    cola.terminar(t.ejecucionId(), "COMPLETADA", r.paso(), null, r.mensaje());
                    if (diferir && r.real()) {
                        synchronized (s) {
                            s.sinGuardar.add(t.ejecucionId());
                        }
                    }
                }
                case YA_TENIA, EXCLUIDA -> cola.terminar(t.ejecucionId(), "EXCLUIDA_AL_EJECUTAR", r.paso(), r.codigoFallo(), r.mensaje());
                case FALLIDA -> {
                    cola.terminar(t.ejecucionId(), "FALLIDA", r.paso(), r.codigoFallo(), r.mensaje());
                    if ("P08".equals(r.paso())) {
                        // Solo una falla en la consola puede dejar la sesión en mal estado.
                        synchronized (s) {
                            s.descartar();
                        }
                    }
                }
            }
        } catch (RuntimeException e) {
            cola.terminar(t.ejecucionId(), "FALLIDA", "P03", "FALLO_SISTEMA_NO_RESPONDE", "El ejecutor no pudo procesar la ONU: " + e.getMessage());
        }
    }

    /** Al terminar con la OLT (no quedan ONUs, parada o detención): guarda lo pendiente y cierra la sesión. */
    public void cerrar(SesionOlt s) {
        s.esperarParalelas();
        synchronized (s) {
            guardar(s, null);
            s.descartar();
        }
    }

    /**
     * Un solo save para las ONUs cambiadas desde el anterior. Si la sesión se cayó, se abre otra: el save guarda toda
     * la configuración en uso de la OLT. Si la OLT no confirma, esas ONUs quedan fallidas con FALLO_NO_GUARDADO.
     */
    private void guardar(SesionOlt s, Long loteId) {
        if (s.sinGuardar.isEmpty()) {
            return;
        }
        List<Long> grupo = List.copyOf(s.sinGuardar);
        s.sinGuardar.clear();
        // Si la sesión se cayó (LOT-2026-000053: "invalid packet length"), se abre otra y se guarda de nuevo una vez:
        // el save guarda toda la configuración en uso, así que los cambios de la sesión caída también quedan.
        boolean guardo = intentarGuardar(s, grupo, loteId);
        if (!guardo) {
            s.descartar();
            guardo = intentarGuardar(s, grupo, loteId);
        }
        if (!guardo) {
            for (long id : grupo) {
                cola.terminar(id, "FALLIDA", "P07", "FALLO_NO_GUARDADO",
                        "El cambio quedó aplicado y verificado, pero la OLT no confirmó que guardó la configuración del grupo.");
            }
        }
    }

    /** Un save con su espera; registra el resultado en la auditoría. */
    private boolean intentarGuardar(SesionOlt s, List<Long> grupo, Long loteId) {
        String respuesta;
        boolean guardo;
        try {
            OltConsolePort consola = s.compartida();
            respuesta = consola.ejecutar("save");
            for (int i = 0; i < CambioDePerfilUnaOnu.REINTENTOS_SI_OCUPADA && RespuestaHuawei.estaOcupada(respuesta); i++) {
                Thread.sleep(CambioDePerfilUnaOnu.ESPERA_SI_OCUPADA.toMillis());
                respuesta = consola.ejecutar("save");
            }
            guardo = !RespuestaHuawei.esError(respuesta);
            if (guardo && !CambioDePerfilUnaOnu.GUARDADO_TERMINADO.matcher(respuesta).find()) {
                consola.esperarTexto(CambioDePerfilUnaOnu.GUARDADO_TERMINADO, CambioDePerfilUnaOnu.ESPERA_GUARDADO);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            respuesta = "Interrumpido";
            guardo = false;
        } catch (RuntimeException e) {
            respuesta = String.valueOf(e.getMessage());
            guardo = false;
            s.descartar();
        }
        auditoria.record(AuditEntry.builder(ActorType.PROCESO_EJECUTOR, guardo ? AuditAction.COMANDO_RESPONDIDO : AuditAction.PASO_FALLIDO)
                .loteId(loteId).sistema(s.olt()).paso("P07").peticion(java.util.Map.of("comando", "save", "onus", grupo.size()))
                .resultado(guardo ? "OK" : "FALLO")
                .mensaje(guardo ? "Configuración de " + s.olt() + " guardada para " + grupo.size() + " ONUs."
                        : "La OLT " + s.olt() + " no confirmó el guardado de " + grupo.size() + " ONUs.")
                .build());
        return guardo;
    }
}
