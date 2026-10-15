package com.guajiranet.migradoronu.ejecucion.aplicacion;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.guajiranet.migradoronu.comun.puertos.AuditAction;
import com.guajiranet.migradoronu.comun.puertos.AuditEntry;
import com.guajiranet.migradoronu.comun.puertos.AuditTrailPort;
import com.guajiranet.migradoronu.ejecucion.aplicacion.CambioDePerfilUnaOnu.Contexto;
import com.guajiranet.migradoronu.ejecucion.aplicacion.CambioDePerfilUnaOnu.Final;
import com.guajiranet.migradoronu.ejecucion.aplicacion.CambioDePerfilUnaOnu.Objetivo;
import com.guajiranet.migradoronu.ejecucion.aplicacion.CambioDePerfilUnaOnu.Paso;
import com.guajiranet.migradoronu.ejecucion.aplicacion.CambioDePerfilUnaOnu.Resultado;
import com.guajiranet.migradoronu.ejecucion.puertos.salida.GenieAcsPort;
import com.guajiranet.migradoronu.ejecucion.puertos.salida.GestionSmartOltPort;
import com.guajiranet.migradoronu.ejecucion.puertos.salida.OltConsolePort;

/**
 * Cierre del procedimiento del NOC en una ONU, probado de punta a punta en OLT_FONSECA 0/5/11 ONT 55 el 14 de octubre:
 * <ol>
 *   <li>P03: lee en vivo en GenieACS el X_HW_SERVICELIST de sus conexiones WAN.</li>
 *   <li>P05: si hace falta, INTERNET a TR069_INTERNET y TR069 a OTHER (el TR-069 pasa a la IP de servicio).</li>
 *   <li>P06: verifica en GenieACS (la ONU puede tardar unos minutos: corta la sesión al cambiar sus conexiones).</li>
 *   <li>P07: quita la VLAN de gestión en SmartOLT (que de paso le quita el perfil TR-069 en la OLT).</li>
 *   <li>P08: le vuelve a poner GUAJIRANET1 en la OLT, para que quede registrado y por si la ONU se resetea.</li>
 * </ol>
 * Si algo no se puede verificar, se detiene sin quitar la VLAN: así se puede reintentar. Nada se manda dos veces a ciegas.
 */
public class CambioDeServicioWanUnaOnu {

    /** Cada cuánto se vuelve a mirar GenieACS (la ONU se reporta cada pocos segundos desde que bajaron el intervalo). */
    static final Duration ESPERA = Duration.ofSeconds(5);
    /** Hasta 5 minutos esperando la verificación (con el reinicio a los 30 s, la ONU confirma en un par de minutos). */
    static final int VERIFICACIONES = 60;
    /** Hasta 5 minutos esperando que la ONU se reporte para la lectura previa (tras el reinicio a los 30 s tarda 2-3 min en prender). */
    static final int ESPERAS_LECTURA = 60;
    /**
     * Si la ONU no se reporta en este tiempo, se reinicia por SmartOLT (una vez en la lectura y una en la verificación):
     * al prender se reporta enseguida y recibe lo pendiente. Probado el 14 de octubre: de ~15 minutos a ~75 segundos.
     */
    static final Duration REINICIAR_SI_NO_CONFIRMA = Duration.ofSeconds(30);
    static final List<String> RUTAS_POR_DEFECTO = List.of(
            "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.X_HW_SERVICELIST",
            "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANIPConnection.1.X_HW_SERVICELIST");

    private final GenieAcsPort genie;
    private final GestionSmartOltPort smartOlt;
    private final CambioDePerfilUnaOnu perfil;
    private final AuditTrailPort auditoria;
    private final Consumer<Duration> esperar;

    public CambioDeServicioWanUnaOnu(GenieAcsPort genie, GestionSmartOltPort smartOlt, CambioDePerfilUnaOnu perfil, AuditTrailPort auditoria) {
        this(genie, smartOlt, perfil, auditoria, d -> {
            try {
                Thread.sleep(d.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrumpido esperando a GenieACS", e);
            }
        });
    }

    CambioDeServicioWanUnaOnu(GenieAcsPort genie, GestionSmartOltPort smartOlt, CambioDePerfilUnaOnu perfil, AuditTrailPort auditoria,
                              Consumer<Duration> esperar) {
        this.genie = Objects.requireNonNull(genie, "genie");
        this.smartOlt = Objects.requireNonNull(smartOlt, "smartOlt");
        this.perfil = Objects.requireNonNull(perfil, "perfil");
        this.auditoria = Objects.requireNonNull(auditoria, "auditoria");
        this.esperar = Objects.requireNonNull(esperar, "esperar");
    }

    public Resultado ejecutar(TrabajoOnu t, boolean real, Contexto ctx, Supplier<OltConsolePort> consola) {
        return ejecutar(t, real, ctx, consola, new Object(), false);
    }

    /**
     * @param candado        lo que se bloquea para usar la consola de la OLT (P08): varias ONUs de la misma OLT pueden ir a
     *                       la vez esperando a GenieACS, pero a la consola entran de a una
     * @param diferirGuardado true si el save de la OLT lo hace quien llama, una vez para varias ONUs
     */
    public Resultado ejecutar(TrabajoOnu t, boolean real, Contexto ctx, Supplier<OltConsolePort> consola, Object candado, boolean diferirGuardado) {
        Registro r = new Registro(t, ctx);
        String posicion = t.oltCodigo() + " " + t.tarjeta() + "/" + t.pon() + " ONT " + t.onuId();
        // P03: lectura en vivo (lo guardado en GenieACS puede tener meses).
        EstadoServicioWan antes;
        try {
            Optional<EstadoServicioWan> guardado = genie.leer(t.sn());
            if (guardado.isEmpty()) {
                r.paso("P03", "GenieACS: buscar " + t.sn(), "", false, "La ONU no está en GenieACS.");
                return fallarYLimpiar(r, t, real, ctx, consola, candado, diferirGuardado, "P03", "FALLO_FOTO_PREVIA",
                        "La ONU " + t.sn() + " no está registrada en GenieACS.");
            }
            // Si GenieACS nunca leyó sus conexiones, se piden las dos de siempre (1: internet, 2: TR-069).
            List<String> rutas = new ArrayList<>(guardado.get().servicios().keySet());
            if (rutas.isEmpty()) {
                rutas = RUTAS_POR_DEFECTO;
            }
            long informAntes = guardado.get().ultimoInformMs();
            boolean respondio = genie.refrescar(guardado.get().idDispositivo(), rutas);
            antes = genie.leer(t.sn()).orElse(guardado.get());
            // Si GenieACS no la alcanza al instante, la lectura queda pendiente hasta que la ONU se reporte sola (cada 30 s
            // por la VLAN de gestión): se espera hasta 5 minutos a que lo haga con sus conexiones leídas.
            boolean reiniciadaEnLectura = false;
            boolean seReporto = false;
            for (int i = 0; i < ESPERAS_LECTURA && !respondio; i++) {
                esperar.accept(ESPERA);
                // Solo se reinicia si está callada: si ya se reportó, reiniciarla solo la demora (LOT-2026-000125).
                if (real && !reiniciadaEnLectura && !seReporto && (long) (i + 1) * ESPERA.toMillis() >= REINICIAR_SI_NO_CONFIRMA.toMillis()) {
                    reiniciadaEnLectura = true;
                    reiniciar(r, t, "P03", "no se reportaba para la lectura", consola, candado);
                }
                EstadoServicioWan x = genie.leer(t.sn()).orElse(antes);
                if (x.ultimoInformMs() > informAntes && !seReporto) {
                    seReporto = true;
                    // Se reportó pero sin las conexiones leídas: se vuelve a pedir la lectura para el próximo reporte.
                    try {
                        genie.refrescar(x.idDispositivo(), rutas);
                    } catch (RuntimeException ignorada) {
                        // Se sigue esperando.
                    }
                    x = genie.leer(t.sn()).orElse(x);
                }
                if (x.ultimoInformMs() > informAntes && x.rutaServicio().isPresent() && x.rutaTr069().isPresent()) {
                    if (i > 0 || x.servicios().keySet().containsAll(rutas)) {
                        antes = x;
                        respondio = true;
                    }
                }
            }
            if (!respondio) {
                r.paso("P03", "GenieACS: leer X_HW_SERVICELIST en vivo", antes.describir(), false, "La ONU no respondió a GenieACS.");
                return fallarYLimpiar(r, t, real, ctx, consola, candado, diferirGuardado, "P03", "FALLO_SISTEMA_NO_RESPONDE",
                        "La ONU no se reportó a GenieACS en 5 minutos, ni reiniciándola.");
            }
        } catch (RuntimeException e) {
            return r.fin(real, Final.FALLIDA, "P03", "FALLO_SISTEMA_NO_RESPONDE", "No se pudo consultar GenieACS: " + e.getMessage());
        }
        r.paso("P03", "GenieACS: leer X_HW_SERVICELIST en vivo", antes.describir(), true, "Foto previa en GenieACS: " + antes.describir() + ".");
        Optional<String> servicio = antes.rutaServicio();
        Optional<String> tr069 = antes.rutaTr069();
        if (servicio.isEmpty() || tr069.isEmpty()) {
            return fallarYLimpiar(r, t, real, ctx, consola, candado, diferirGuardado, "P03", "FALLO_FOTO_PREVIA",
                    "La ONU no tiene las dos conexiones esperadas (INTERNET y TR069): " + antes.describir() + ".");
        }
        boolean yaListo = antes.listo();
        Map<String, String> cambios = new LinkedHashMap<>();
        if (!yaListo) {
            cambios.put(servicio.get(), "TR069_INTERNET");
            cambios.put(tr069.get(), "OTHER");
        }
        if (!real) {
            if (!yaListo) {
                r.paso("SIM", "GenieACS: " + servicio.get() + " = TR069_INTERNET; " + tr069.get() + " = OTHER", "", true, "Simulación: no se mandó.");
            }
            r.paso("SIM", "SmartOLT: IP de gestión Inactiva", "", true, "Simulación: no se mandó.");
            r.paso("SIM", "OLT: perfil " + t.perfilDestino(), "", true, "Simulación: no se mandó.");
            return r.fin(false, Final.SIMULADA, "P05", null, yaListo ? "Simulación: ya tiene TR069_INTERNET/OTHER; solo quedaría quitar la VLAN 46."
                    : "Simulación terminada: la ONU cumple las condiciones; no se cambió nada.");
        }
        if (!yaListo) {
            // P05: un solo envío. La ONU suele cortar la sesión al cambiar sus conexiones: se verifica en P06.
            boolean confirmo;
            try {
                confirmo = genie.cambiar(antes.idDispositivo(), cambios);
            } catch (RuntimeException e) {
                confirmo = false;
            }
            r.paso("P05", "GenieACS: " + servicio.get() + " = TR069_INTERNET; " + tr069.get() + " = OTHER", confirmo ? "done" : "sin confirmar",
                    true, confirmo ? "La ONU aceptó el cambio." : "Cambio enviado; la ONU no lo confirmó en el momento (normal: reinicia sus conexiones).");
            // P06: verificar.
            EstadoServicioWan despues = antes;
            boolean reiniciada = false;
            for (int i = 0; i < VERIFICACIONES && !despues.listo(); i++) {
                esperar.accept(ESPERA);
                if (!reiniciada && despues.ultimoInformMs() <= antes.ultimoInformMs()
                        && (long) (i + 1) * ESPERA.toMillis() >= REINICIAR_SI_NO_CONFIRMA.toMillis()) {
                    reiniciada = true;
                    reiniciar(r, t, "P06", "no confirmaba el cambio", consola, candado);
                }
                try {
                    if (i % 6 == 5) {
                        genie.refrescar(antes.idDispositivo(), List.of(servicio.get(), tr069.get()));
                    }
                    despues = genie.leer(t.sn()).orElse(despues);
                } catch (RuntimeException ignorada) {
                    // Se vuelve a intentar en la siguiente vuelta.
                }
            }
            boolean quedo = despues.listo();
            r.paso("P06", "GenieACS: leer X_HW_SERVICELIST", despues.describir(), quedo, quedo ? "Verificado en GenieACS: " + despues.describir() + "."
                    : "GenieACS no muestra el cambio: " + despues.describir() + ".");
            if (!quedo) {
                return fallarYLimpiar(r, t, true, ctx, consola, candado, diferirGuardado, "P06", "FALLO_VERIFICACION",
                        "GenieACS no confirmó TR069_INTERNET/OTHER en 5 minutos, ni reiniciándola.");
            }
        }
        String[] error = limpiar(r, t, ctx, consola, candado, diferirGuardado);
        if (error != null) {
            return r.fin(true, Final.FALLIDA, error[0], error[1], "El servicio WAN quedó listo, pero " + error[2]);
        }
        return r.fin(true, Final.COMPLETADA, "P08", null, "Listo: " + posicion + (yaListo ? " ya tenía TR069_INTERNET/OTHER" : " pasó a TR069_INTERNET/OTHER")
                + ", sin VLAN 46 y con " + t.perfilDestino() + ". Se reporta a GenieACS por la IP de servicio.");
    }

    /**
     * Falla y deja la ONU limpia: sin VLAN 46 ni TR-069 en SmartOLT y con GUAJIRANET1 en la OLT, para que no quede a medias
     * con la gestión prendida. En simulación no se toca nada.
     */
    private Resultado fallarYLimpiar(Registro r, TrabajoOnu t, boolean real, Contexto ctx, Supplier<OltConsolePort> consola, Object candado,
                                     boolean diferirGuardado, String paso, String codigo, String mensaje) {
        if (!real) {
            return r.fin(false, Final.FALLIDA, paso, codigo, mensaje);
        }
        String[] error = limpiar(r, t, ctx, consola, candado, diferirGuardado);
        return r.fin(true, Final.FALLIDA, paso, codigo, mensaje + (error == null
                ? " Se dejó sin VLAN 46 ni TR-069 y con " + t.perfilDestino() + "; se puede volver a empezar desde el paso 1."
                : " Además, al dejarla limpia: " + error[2]));
    }

    /**
     * P07 y P08: quita la VLAN de gestión (y el TR-069) en SmartOLT y vuelve a poner el perfil en la OLT (SmartOLT lo quita
     * al desactivar la gestión). Devuelve null si quedó bien, o {paso, código, mensaje} si no.
     */
    private String[] limpiar(Registro r, TrabajoOnu t, Contexto ctx, Supplier<OltConsolePort> consola, Object candado, boolean diferirGuardado) {
        try {
            Optional<EstadoGestionOnu> g = smartOlt.leer(t.sn());
            if (g.isPresent() && g.get().modo() != null && !g.get().modo().equalsIgnoreCase("Inactive")) {
                smartOlt.desactivarGestion(g.get().idExterno());
                r.paso("P07", "SmartOLT: IP de gestión Inactiva (" + g.get().idExterno() + ")", "OK", true, "VLAN de gestión y TR-069 quitados en SmartOLT.");
            } else {
                r.paso("P07", "SmartOLT: IP de gestión", g.map(EstadoGestionOnu::describir).orElse("sin dato"), true, "Ya no tenía la VLAN de gestión.");
            }
        } catch (RuntimeException e) {
            r.paso("P07", "SmartOLT: IP de gestión Inactiva", String.valueOf(e.getMessage()), false, "SmartOLT no quitó la VLAN de gestión.");
            return new String[] {"P07", "FALLO_OLT_RECHAZO", "SmartOLT no quitó la VLAN 46: " + e.getMessage()};
        }
        String[] tarjeta = t.tarjeta().split("/");
        Objetivo objetivo = new Objetivo(t.oltCodigo(), Integer.parseInt(tarjeta[0].strip()), Integer.parseInt(tarjeta[1].strip()), t.pon(), t.onuId(),
                List.of(CambioDePerfilUnaOnu.SIN_PERFIL, "SmartOLT", "GUAJIRANET"), t.perfilDestino(), t.guardar() && !diferirGuardado, false, t.sn());
        Resultado p;
        synchronized (candado) {
            p = perfil.ejecutar(objetivo, true, ctx, consola);
        }
        boolean perfilOk = p.fin() == Final.COMPLETADA || p.fin() == Final.YA_TENIA;
        r.paso("P08", "OLT: perfil " + t.perfilDestino(), p.mensaje(), perfilOk, perfilOk ? "Perfil " + t.perfilDestino() + " en la OLT." : p.mensaje());
        if (!perfilOk) {
            return new String[] {"P08", p.codigoFallo() == null ? "FALLO_OLT_RECHAZO" : p.codigoFallo(),
                    "quedó sin VLAN 46 y no se pudo volver a poner " + t.perfilDestino() + ": " + p.mensaje()};
        }
        return null;
    }

    /**
     * Reinicia la ONU para que se reporte ya. Primero en la OLT ({@code ont reset}, por OMCI: no depende de SmartOLT ni del
     * TR-069). El reboot de SmartOLT respondía "Device reboot command sent" sin reiniciar algunas ONUs (14 de octubre):
     * queda solo como respaldo si la OLT no acepta el comando. Si nada funciona, se sigue esperando igual.
     */
    private void reiniciar(Registro r, TrabajoOnu t, String paso, String porque, Supplier<OltConsolePort> consola, Object candado) {
        String comando = "ont reset " + t.pon() + " " + t.onuId();
        String respuesta;
        boolean bien;
        synchronized (candado) {
            try {
                OltConsolePort c = consola.get();
                c.ejecutar("enable");
                c.ejecutar("config");
                c.ejecutar("interface gpon " + t.tarjeta().strip());
                respuesta = c.ejecutar(comando);
                c.ejecutar("quit");
                bien = !com.guajiranet.migradoronu.procedimientos.dominio.RespuestaHuawei.esError(respuesta);
            } catch (RuntimeException e) {
                respuesta = String.valueOf(e.getMessage());
                bien = false;
            }
        }
        r.paso(paso, "OLT: " + comando + " (tarjeta " + t.tarjeta() + ")", respuesta, bien, bien
                ? "La ONU " + porque + " en " + REINICIAR_SI_NO_CONFIRMA.toSeconds() + " s: se reinició desde la OLT para que se reporte ya."
                : "La OLT no reinició la ONU; se intenta por SmartOLT.");
        if (bien) {
            return;
        }
        try {
            Optional<EstadoGestionOnu> g = smartOlt.leer(t.sn());
            if (g.isEmpty()) {
                return;
            }
            smartOlt.reiniciar(g.get().idExterno());
            r.paso(paso, "SmartOLT: reiniciar ONU (" + g.get().idExterno() + ")", "OK", true, "Reinicio pedido a SmartOLT.");
        } catch (RuntimeException e) {
            r.paso(paso, "SmartOLT: reiniciar ONU", String.valueOf(e.getMessage()), false, "No se pudo reiniciar; se sigue esperando.");
        }
    }

    /** Los pasos de una ejecución y su auditoría (igual que en los otros cambios). */
    private final class Registro {
        private final TrabajoOnu t;
        private final Contexto ctx;
        private final List<Paso> pasos = new ArrayList<>();
        private String ultimoPaso = "P02";

        Registro(TrabajoOnu t, Contexto ctx) {
            this.t = t;
            this.ctx = ctx;
        }

        void paso(String codigo, String comando, String respuesta, boolean bien, String mensaje) {
            pasos.add(new Paso(codigo, comando, respuesta, bien, mensaje));
            boolean simulado = codigo.equals("SIM");
            if (!simulado && !codigo.equals(ultimoPaso)) {
                ultimoPaso = codigo;
                try {
                    ctx.alAvanzar().accept(codigo);
                } catch (RuntimeException ignorada) {
                    // Que no se pueda mostrar el avance no detiene el cambio.
                }
            }
            Map<String, Object> peticion = new LinkedHashMap<>();
            peticion.put("comando", comando);
            peticion.put("onu", t.oltCodigo() + " " + t.tarjeta() + "/" + t.pon() + " ONT " + t.onuId());
            auditoria.record(base(simulado ? AuditAction.PASO_SIMULADO : bien ? AuditAction.COMANDO_RESPONDIDO : AuditAction.PASO_FALLIDO)
                    .paso(simulado ? null : codigo).peticion(peticion).respuesta(Map.of("texto", respuesta == null ? "" : respuesta))
                    .resultado(bien ? "OK" : "FALLO").mensaje(mensaje).build());
        }

        Resultado fin(boolean real, Final fin, String paso, String codigo, String mensaje) {
            AuditAction accion = switch (fin) {
                case COMPLETADA -> AuditAction.ONU_COMPLETADA;
                case FALLIDA -> AuditAction.ONU_FALLIDA;
                case EXCLUIDA, YA_TENIA -> AuditAction.ONU_EXCLUIDA_AL_EJECUTAR;
                case SIMULADA -> AuditAction.PASO_SIMULADO;
            };
            auditoria.record(base(accion).paso(paso).codigo(codigo).peticion(Map.of("onu", t.oltCodigo() + " " + t.tarjeta() + "/" + t.pon()
                    + " ONT " + t.onuId(), "real", real, "cambio", "Servicio WAN")).resultado(fin.name()).mensaje(mensaje).build());
            return new Resultado(real, fin, mensaje, t.sn(), codigo, paso, List.copyOf(pasos));
        }

        private AuditEntry.Builder base(AuditAction accion) {
            return AuditEntry.builder(ctx.actor(), accion).usuarioId(ctx.usuarioId()).loteId(ctx.loteId())
                    .ejecucionId(ctx.ejecucionId()).sn(t.sn()).sistema("GenieACS");
        }
    }
}
