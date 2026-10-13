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

    public EjecutorDeOnus(ColaDeEjecucionPort cola, OltConsoleFactoryPort consolas, CambioDePerfilUnaOnu cambio, String perfilOrigen,
                          AuditTrailPort auditoria) {
        this.cola = Objects.requireNonNull(cola, "cola");
        this.consolas = Objects.requireNonNull(consolas, "consolas");
        this.cambio = Objects.requireNonNull(cambio, "cambio");
        this.perfilOrigen = Objects.requireNonNull(perfilOrigen, "perfilOrigen");
        this.auditoria = Objects.requireNonNull(auditoria, "auditoria");
    }

    /** La sesión de un hilo con una OLT y las ONUs ya cambiadas que esperan el próximo save. */
    public final class SesionOlt {
        private final String olt;
        private OltConsolePort real;
        private final List<Long> sinGuardar = new ArrayList<>();

        private SesionOlt(String olt) {
            this.olt = olt;
        }

        public String olt() {
            return olt;
        }

        /** La consola compartida: no se cierra al terminar una ONU y no se sale de la sesión con el último quit. */
        private OltConsolePort compartida() {
            if (real == null) {
                real = consolas.abrir(olt);
            }
            OltConsolePort consola = real;
            return new OltConsolePort() {
                @Override
                public String ejecutar(String comando) {
                    if (comando.strip().equals("quit") && !consola.prompt().contains("(config")) {
                        return "";
                    }
                    return consola.ejecutar(comando);
                }

                @Override
                public String prompt() {
                    return consola.prompt();
                }

                @Override
                public boolean esperarTexto(Pattern patron, java.time.Duration maximo) {
                    return consola.esperarTexto(patron, maximo);
                }

                @Override
                public void close() {
                    // La cierra el ejecutor al terminar con la OLT.
                }
            };
        }

        private void descartar() {
            if (real != null) {
                try {
                    real.close();
                } catch (RuntimeException ignorada) {
                    // Ya estaba caída.
                }
                real = null;
            }
        }
    }

    public SesionOlt nuevaSesion(String olt) {
        return new SesionOlt(olt);
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
        boolean enGrupo = onusPorGuardado > 1 && t.guardar();
        try {
            String[] tarjeta = t.tarjeta().split("/");
            Objetivo objetivo = new Objetivo(t.oltCodigo(), Integer.parseInt(tarjeta[0].strip()), Integer.parseInt(tarjeta[1].strip()),
                    t.pon(), t.onuId(), t.perfilesOrigen() == null ? List.of(perfilOrigen, CambioDePerfilUnaOnu.SIN_PERFIL) : t.perfilesOrigen(),
                    t.perfilDestino(), t.guardar() && !enGrupo, false, t.sn());
            Contexto ctx = new Contexto(ActorType.PROCESO_EJECUTOR, t.confirmadoPor(), t.loteId(), t.ejecucionId(),
                    paso -> cola.avanzarPaso(t.ejecucionId(), paso));
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

    /** Al terminar con la OLT (no quedan ONUs, parada o detención): guarda lo pendiente y cierra la sesión. */
    public void cerrar(SesionOlt s) {
        guardar(s, null);
        s.descartar();
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
