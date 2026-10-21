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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 *   <li>P03: foto previa: el X_HW_SERVICELIST que tiene GenieACS y, si ahí no está listo, las WAN según la OLT.</li>
 *   <li>P05: si hace falta, le pone la etiqueta {@value #ETIQUETA}: el preset de GenieACS le cambia INTERNET a
 *       TR069_INTERNET y TR069 a OTHER en cuanto se reporte (el TR-069 pasa a la IP de servicio).</li>
 *   <li>P06: verifica en GenieACS y en la OLT (la ONU corta la sesión al cambiar sus conexiones y GenieACS puede tardar
 *       en registrarla).</li>
 *   <li>P07: quita la VLAN de gestión en SmartOLT (que de paso le quita el perfil TR-069 en la OLT).</li>
 *   <li>P08: le vuelve a poner GUAJIRANET1 en la OLT, para que quede registrado y por si la ONU se resetea.</li>
 * </ol>
 * Si algo no se puede verificar, se detiene sin quitar la VLAN: así se puede reintentar. Nada se manda dos veces a ciegas.
 */
public class CambioDeServicioWanUnaOnu {

    /** Cada cuánto se vuelve a mirar GenieACS (la ONU se reporta cada pocos segundos desde que bajaron el intervalo). */
    static final Duration ESPERA = Duration.ofSeconds(5);
    /**
     * Hasta 2 minutos esperando la verificación: con la etiqueta, todas las que salieron bien el 16 de octubre confirmaron
     * en 25 a 40 s (antes se esperaban 5 minutos y solo alargaba las que no iban a responder).
     */
    static final int VERIFICACIONES = 24;
    /** Un reporte a GenieACS más viejo que esto no sirve para decir que la ONU ya se reporta por internet. */
    static final Duration REPORTE_RECIENTE = Duration.ofDays(2);
    /** La etiqueta de GenieACS que activa el preset "migrador_servicio_wan" (creado el 16 de octubre). */
    public static final String ETIQUETA = "migrar_wan";
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
        // P03: foto previa. GenieACS puede estar viejo (la ONU solo actualiza lo que reporta); la OLT dice lo que la ONU
        // tiene de verdad (display ont wan-info), sin depender de que se reporte.
        EstadoServicioWan antes;
        try {
            Optional<EstadoServicioWan> guardado = genie.leer(t.sn());
            if (guardado.isEmpty()) {
                r.paso("P03", "GenieACS: buscar " + t.sn(), "", false, "La ONU no está en GenieACS.");
                return fallarYLimpiar(r, t, real, ctx, consola, candado, diferirGuardado, "P03", "FALLO_FOTO_PREVIA",
                        "La ONU " + t.sn() + " no está registrada en GenieACS.");
            }
            antes = guardado.get();
        } catch (RuntimeException e) {
            return r.fin(real, Final.FALLIDA, "P03", "FALLO_SISTEMA_NO_RESPONDE", "No se pudo consultar GenieACS: " + e.getMessage());
        }
        boolean yaListo = antes.listo();
        if (yaListo) {
            r.paso("P03", "GenieACS: X_HW_SERVICELIST guardado", antes.describir(), true,
                    "GenieACS ya la muestra con TR069_INTERNET/OTHER: solo falta quitar la VLAN 46.");
        } else {
            Optional<String> wan = leerWanEnOlt(t, consola, candado);
            yaListo = wan.map(CambioDeServicioWanUnaOnu::listoEnOlt).orElse(false);
            r.paso("P03", comandoWan(t), wan.orElse("sin respuesta de la OLT"), true, yaListo
                    ? "La OLT muestra la ONU con Tr069+Internet / Other: el cambio ya está hecho (GenieACS aún no se enteró)."
                    : "Foto previa: GenieACS " + antes.describir() + "; OLT " + wan.map(CambioDeServicioWanUnaOnu::servicios).orElse("sin dato") + ".");
        }
        // Con valores raros en GenieACS (por ejemplo dos TR069_INTERNET) no se toca: puede ser otra configuración.
        if (!yaListo && !antes.servicios().isEmpty() && (antes.rutaServicio().isEmpty() || antes.rutaTr069().isEmpty())) {
            return fallarYLimpiar(r, t, real, ctx, consola, candado, diferirGuardado, "P03", "FALLO_FOTO_PREVIA",
                    "La ONU no tiene las dos conexiones esperadas (INTERNET y TR069): " + antes.describir() + ".");
        }
        if (!real) {
            if (!yaListo) {
                r.paso("SIM", "GenieACS: etiqueta " + ETIQUETA + " (INTERNET = TR069_INTERNET; TR069 = OTHER)", "", true, "Simulación: no se mandó.");
            }
            r.paso("SIM", "SmartOLT: IP de gestión Inactiva", "", true, "Simulación: no se mandó.");
            r.paso("SIM", "OLT: perfil " + t.perfilDestino(), "", true, "Simulación: no se mandó.");
            return r.fin(false, Final.SIMULADA, "P05", null, yaListo ? "Simulación: ya tiene TR069_INTERNET/OTHER; solo quedaría quitar la VLAN 46."
                    : "Simulación terminada: la ONU cumple las condiciones; no se cambió nada.");
        }
        if (!yaListo) {
            String id = antes.idDispositivo();
            // Fallos viejos en GenieACS hacen que lo nuevo espere detrás de reintentos cada vez más largos: se borran primero.
            int borrados = genie.limpiarFallos(id);
            if (borrados > 0) {
                r.paso("P05", "GenieACS: borrar fallos del equipo", borrados + " fallo(s)", true,
                        "Se borraron " + borrados + " fallo(s) viejos de la ONU en GenieACS para que el cambio no espere detrás.");
            }
            List<String> rutas = antes.servicios().isEmpty() ? RUTAS_POR_DEFECTO : new ArrayList<>(antes.servicios().keySet());
            // P05: con la etiqueta, el preset de GenieACS hace el cambio la próxima vez que la ONU se reporte, sin que
            // GenieACS tenga que alcanzarla (con IP de gestión repetida no la alcanza). Se la despierta por si acaso, sin
            // esperar: si GenieACS no la alcanza tarda ~30 s en rendirse (LOT-2026-000149).
            java.util.concurrent.atomic.AtomicBoolean despertando = new java.util.concurrent.atomic.AtomicBoolean();
            if (genie.etiquetar(id, ETIQUETA, true)) {
                despertarSinEsperar(id, rutas, despertando);
                r.paso("P05", "GenieACS: etiqueta " + ETIQUETA, "esperando su reporte", true,
                        "Etiqueta puesta: el preset de GenieACS le cambia INTERNET a TR069_INTERNET y TR069 a OTHER en cuanto se reporte.");
            } else {
                // Sin la etiqueta, el cambio se manda como antes, si se sabe qué conexión es cuál.
                Optional<String> servicio = antes.rutaServicio();
                Optional<String> tr069 = antes.rutaTr069();
                if (servicio.isEmpty() || tr069.isEmpty()) {
                    r.paso("P05", "GenieACS: etiqueta " + ETIQUETA, "rechazada", false, "GenieACS no aceptó la etiqueta.");
                    return fallarYLimpiar(r, t, true, ctx, consola, candado, diferirGuardado, "P05", "FALLO_SISTEMA_NO_RESPONDE",
                            "GenieACS no aceptó la etiqueta " + ETIQUETA + " y no tiene leídas las conexiones de la ONU.");
                }
                boolean confirmo;
                try {
                    confirmo = genie.cambiar(id, Map.of(servicio.get(), "TR069_INTERNET", tr069.get(), "OTHER"));
                } catch (RuntimeException e) {
                    confirmo = false;
                }
                r.paso("P05", "GenieACS: " + servicio.get() + " = TR069_INTERNET; " + tr069.get() + " = OTHER", confirmo ? "done" : "sin confirmar",
                        true, confirmo ? "La ONU aceptó el cambio." : "Cambio enviado; la ONU no lo confirmó en el momento (normal: reinicia sus conexiones).");
            }
            // P06: verificar en GenieACS y en la OLT cada 5 s (GenieACS tarda ~10 minutos en registrar la sesión en que la
            // ONU cambió: 3DF122AE, 16 de octubre; la OLT lo muestra al instante). Cada 30 s se la vuelve a despertar.
            EstadoServicioWan despues = antes;
            String verificadoEn = null;
            for (int i = 0; i < VERIFICACIONES && verificadoEn == null; i++) {
                esperar.accept(ESPERA);
                try {
                    despues = genie.leer(t.sn()).orElse(despues);
                } catch (RuntimeException ignorada) {
                    // Se vuelve a intentar en la siguiente vuelta.
                }
                if (despues.listo()) {
                    verificadoEn = "GenieACS: " + despues.describir();
                } else {
                    Optional<String> wan = leerWanEnOlt(t, consola, candado);
                    if (wan.map(CambioDeServicioWanUnaOnu::listoEnOlt).orElse(false)) {
                        verificadoEn = "la OLT: " + servicios(wan.get());
                    } else if (i % 6 == 5) {
                        despertarSinEsperar(id, rutas, despertando);
                    }
                }
            }
            boolean quedo = verificadoEn != null;
            r.paso("P06", "GenieACS / OLT: verificar servicio WAN", despues.describir(), quedo, quedo ? "Verificado en " + verificadoEn + "."
                    : "Ni GenieACS ni la OLT muestran el cambio: " + despues.describir() + ".");
            if (!quedo) {
                // Lo que GenieACS dice de ese equipo (por ejemplo, si la ONU rechazó el cambio): queda en la auditoría.
                List<String> fallosGenie = genie.fallos(antes.idDispositivo());
                r.paso("P06", "GenieACS: fallos del equipo", String.join("\n", fallosGenie), fallosGenie.isEmpty(),
                        fallosGenie.isEmpty() ? "GenieACS no tiene fallos de la ONU: el cambio no le llegó o no lo aplicó."
                                : "GenieACS tiene fallos de la ONU: " + String.join(" | ", fallosGenie));
                return fallarYLimpiar(r, t, true, ctx, consola, candado, diferirGuardado, "P06", "FALLO_VERIFICACION",
                        "Ni la OLT ni GenieACS mostraron TR069_INTERNET/OTHER en 2 minutos: la ONU no se reportó a GenieACS por la VLAN 46"
                                + " (lo más común: IP de gestión repetida en el DHCP)."
                                + (fallosGenie.isEmpty() ? "" : " GenieACS dice: " + String.join(" | ", fallosGenie)));
            }
        }
        String[] error = limpiar(r, t, ctx, consola, candado, diferirGuardado);
        if (error != null) {
            return r.fin(true, Final.FALLIDA, error[0], error[1], "El servicio WAN quedó listo, pero " + error[2]);
        }
        // Ya no hace falta la etiqueta: el preset no vuelve a tocar a la ONU.
        boolean sinEtiqueta = genie.etiquetar(antes.idDispositivo(), ETIQUETA, false);
        r.paso("P08", "GenieACS: quitar etiqueta " + ETIQUETA, sinEtiqueta ? "OK" : "no se pudo", true,
                sinEtiqueta ? "Etiqueta quitada." : "No se pudo quitar la etiqueta en GenieACS (no afecta: el preset ya no la cambia).");
        String reinicio = "";
        if (reiniciarAlTerminar) {
            // Sin reiniciar, la ONU tarda ~10 minutos en volver a reportarse por internet (la sesión del cambio se cuelga);
            // reiniciada, se reporta en 1 o 2 minutos (decisión del 16 de octubre).
            String sinReiniciar = reiniciarEnOlt(t, consola, candado);
            r.paso("P08", "OLT: interface gpon " + t.tarjeta().replace(" ", "") + " / ont reset " + t.pon() + " " + t.onuId(),
                    sinReiniciar == null ? "OK" : sinReiniciar, sinReiniciar == null, sinReiniciar == null
                            ? "ONU reiniciada desde la OLT: vuelve en 1 o 2 minutos y se reporta a GenieACS por internet."
                            : "La OLT no reinició la ONU (" + sinReiniciar + "). El cambio ya quedó hecho: GenieACS se pone al día solo en unos 10 minutos.");
            reinicio = sinReiniciar == null ? " Se reinició desde la OLT." : " No se pudo reiniciar desde la OLT; GenieACS se pone al día solo.";
        }
        return r.fin(true, Final.COMPLETADA, "P08", null, "Listo: " + posicion + (yaListo ? " ya tenía TR069_INTERNET/OTHER" : " pasó a TR069_INTERNET/OTHER")
                + ", sin VLAN 46 y con " + t.perfilDestino() + ". Se reporta a GenieACS por la IP de servicio." + reinicio);
    }

    /**
     * Pasos 1 y 2: le pone la etiqueta {@value #ETIQUETA} en GenieACS antes de empezar, para que el preset le haga el
     * cambio de servicio WAN la primera vez que se reporte (FD1281B3, 16 de octubre: con la IP de gestión repetida quedó
     * con un solo reporte, 15 s después del paso 2). Si GenieACS no la tiene o ya está lista, no hace nada. Nunca
     * detiene el paso.
     */
    public void prepararEnGenie(TrabajoOnu t, Contexto ctx) {
        try {
            Optional<EstadoServicioWan> e = genie.leer(t.sn());
            if (e.isEmpty() || e.get().listo()) {
                return;
            }
            boolean ok = genie.etiquetar(e.get().idDispositivo(), ETIQUETA, true);
            auditoria.record(AuditEntry.builder(ctx.actor(), ok ? AuditAction.COMANDO_RESPONDIDO : AuditAction.PASO_FALLIDO)
                    .usuarioId(ctx.usuarioId()).loteId(ctx.loteId()).ejecucionId(ctx.ejecucionId()).sn(t.sn()).sistema("GenieACS")
                    .peticion(Map.of("comando", "GenieACS: etiqueta " + ETIQUETA)).resultado(ok ? "OK" : "FALLO")
                    .mensaje(ok ? "Etiqueta " + ETIQUETA + " puesta: GenieACS le hará el cambio de servicio WAN en cuanto se reporte."
                            : "GenieACS no aceptó la etiqueta " + ETIQUETA + "; el paso 3 la vuelve a intentar.")
                    .build());
        } catch (RuntimeException ignorada) {
            // GenieACS caído no detiene los pasos 1 y 2: el paso 3 vuelve a intentarlo.
        }
    }

    /**
     * Lo que se sabe de la ONU en GenieACS antes de tocarla (lote "Los 3 pasos").
     *
     * @param enGenie            false si GenieACS no la tiene: sin GenieACS no hay paso 3
     * @param reportaPorInternet la IP de internet por la que ya se reporta (su TR-069 ya va por internet), o null
     */
    public record Antes(boolean enGenie, String reportaPorInternet) {
    }

    public Antes revisarAntes(TrabajoOnu t) {
        try {
            Optional<EstadoServicioWan> e = genie.leer(t.sn());
            if (e.isEmpty()) {
                return new Antes(false, null);
            }
            boolean reciente = System.currentTimeMillis() - e.get().ultimoInformMs() <= REPORTE_RECIENTE.toMillis();
            return new Antes(true, reciente ? e.get().reportaPorInternet() : null);
        } catch (RuntimeException ex) {
            // GenieACS no contestó: se sigue; el paso 3 lo vuelve a consultar.
            return new Antes(true, null);
        }
    }

    /**
     * Lote "Los 3 pasos": si falla el paso 1 o el 2, la ONU no se deja a medias: sin VLAN 46 ni TR-069 en SmartOLT y con
     * el perfil destino en la OLT. Devuelve el resultado fallido con lo que pasó al limpiarla.
     */
    public Resultado fallarYDejarLimpia(TrabajoOnu t, Contexto ctx, Supplier<OltConsolePort> consola, Object candado, String paso,
                                        String codigo, String mensaje) {
        return fallarYLimpiar(new Registro(t, ctx), t, !t.simulacion(), ctx, consola, candado, false, paso, codigo, mensaje);
    }

    /** Pide a GenieACS que la despierte (connection request): en esa sesión el preset aplica el cambio. */
    private boolean despertar(String id, List<String> rutas) {
        try {
            return genie.refrescar(id, rutas);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Si al terminar bien el paso 3 se reinicia la ONU desde la OLT (parámetro reiniciar_al_terminar). */
    private volatile boolean reiniciarAlTerminar = true;

    public void reiniciarAlTerminar(boolean reiniciar) {
        this.reiniciarAlTerminar = reiniciar;
    }

    /**
     * Reinicia la ONU desde la OLT ({@code ont reset}, por OMCI: no depende de SmartOLT ni del TR-069; el reboot de SmartOLT
     * respondía sin reiniciar). Devuelve null si la OLT lo aceptó, o lo que respondió.
     */
    private String reiniciarEnOlt(TrabajoOnu t, Supplier<OltConsolePort> consola, Object candado) {
        String tarjeta = t.tarjeta().replace(" ", "");
        synchronized (candado) {
            try {
                OltConsolePort c = consola.get();
                if (c.prompt().contains("-if-")) {
                    c.ejecutar("quit");
                }
                c.ejecutar("enable");
                c.ejecutar("config");
                String entrar = c.ejecutar("interface gpon " + tarjeta);
                if (!c.prompt().contains("gpon-" + tarjeta)) {
                    return "no entró a la tarjeta " + tarjeta + ": " + entrar.strip();
                }
                String respuesta = c.ejecutar("ont reset " + t.pon() + " " + t.onuId());
                c.ejecutar("quit");
                return com.guajiranet.migradoronu.procedimientos.dominio.RespuestaHuawei.esError(respuesta) ? respuesta.strip() : null;
            } catch (RuntimeException e) {
                return String.valueOf(e.getMessage());
            }
        }
    }

    /** Despierta a la ONU en otro hilo, uno a la vez: la verificación sigue mientras GenieACS lo intenta. */
    private void despertarSinEsperar(String id, List<String> rutas, java.util.concurrent.atomic.AtomicBoolean enCurso) {
        if (enCurso.compareAndSet(false, true)) {
            Thread.ofVirtual().name("despertar-" + id).start(() -> {
                try {
                    despertar(id, rutas);
                } finally {
                    enCurso.set(false);
                }
            });
        }
    }

    static String comandoWan(TrabajoOnu t) {
        return "display ont wan-info " + t.tarjeta().replace(" ", "") + " " + t.pon() + " " + t.onuId();
    }

    /** Lee en la OLT las conexiones WAN de la ONU (solo lectura, con la consola de esa OLT). Vacío si no se pudo. */
    private Optional<String> leerWanEnOlt(TrabajoOnu t, Supplier<OltConsolePort> consola, Object candado) {
        synchronized (candado) {
            try {
                OltConsolePort c = consola.get();
                // La consola compartida puede haber quedado dentro de una tarjeta: el comando es de modo configuración.
                if (c.prompt().contains("-if-")) {
                    c.ejecutar("quit");
                }
                c.ejecutar("enable");
                String respuesta = c.ejecutar(comandoWan(t));
                return com.guajiranet.migradoronu.procedimientos.dominio.RespuestaHuawei.esError(respuesta) ? Optional.empty() : Optional.of(respuesta);
            } catch (RuntimeException e) {
                return Optional.empty();
            }
        }
    }

    private static final Pattern TIPO_DE_SERVICIO = Pattern.compile("(?im)^\\s*Service type\\s*:\\s*(.+?)\\s*$");

    /** Los tipos de servicio de sus WAN según la OLT, por ejemplo "Tr069, Internet | Other". */
    static String servicios(String wanInfo) {
        List<String> r = new ArrayList<>();
        Matcher m = TIPO_DE_SERVICIO.matcher(wanInfo);
        while (m.find()) {
            r.add(m.group(1));
        }
        return r.isEmpty() ? "sin conexiones WAN" : String.join(" | ", r);
    }

    /**
     * ¿La OLT muestra el cambio hecho? Una WAN con Tr069 e Internet juntos y otra con Other.
     */
    static boolean listoEnOlt(String wanInfo) {
        boolean tr069Internet = false;
        boolean otra = false;
        Matcher m = TIPO_DE_SERVICIO.matcher(wanInfo);
        while (m.find()) {
            String s = m.group(1).toLowerCase(java.util.Locale.ROOT);
            tr069Internet |= s.contains("tr069") && s.contains("internet");
            otra |= s.equals("other");
        }
        // Una WAN que siga siendo solo de TR-069 no lo impide: mientras la gestión está activa, SmartOLT le crea por OMCI su
        // propia WAN de TR-069 en la VLAN 46 (OLT_C_..._TR069_R_VID_46), que desaparece al quitar la VLAN. Exigir que no
        // existiera dejaba como fallidas a ONUs ya cambiadas (53 de 172 etiquetadas, 21 de octubre).
        return tr069Internet && otra;
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
