package com.guajiranet.migradoronu.ejecucion.aplicacion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.guajiranet.migradoronu.comun.puertos.ActorType;
import com.guajiranet.migradoronu.comun.puertos.AuditAction;
import com.guajiranet.migradoronu.comun.puertos.AuditEntry;
import com.guajiranet.migradoronu.comun.puertos.AuditTrailPort;
import com.guajiranet.migradoronu.ejecucion.puertos.salida.OltConsolePort;
import com.guajiranet.migradoronu.procedimientos.dominio.LecturaOntHuawei;
import com.guajiranet.migradoronu.procedimientos.dominio.RespuestaHuawei;
import com.guajiranet.migradoronu.procedimientos.dominio.TablaDePerfilesHuawei;

/**
 * Cambio del perfil TR-069 de una ONU Huawei por consola. Comandos confirmados por el técnico y probados en
 * OLT-CONEJO 0/0/4 ONT 16 el 7 de octubre de 2026: dentro de {@code interface gpon <F>/<S>},
 * {@code ont tr069-server-config <P> <ONT> profile-id <id>}, luego {@code quit} y {@code save}.
 *
 * <p>Los perfiles se piden por nombre y su ID se lee en la OLT. En simulación solo lee y muestra los comandos que
 * mandaría. En real se detiene en el primer paso que no cuadre y nunca adivina (RN-13). Todo queda en la auditoría.
 */
public class CambioDePerfilUnaOnu {

    /**
     * El perfil intermedio al reaplicar, en orden de preferencia: el que pone SmartOLT y, si la OLT no lo tiene (OLT-CONEJO
     * solo tiene GUAJIRANET y GUAJIRANET1), el viejo GUAJIRANET.
     */
    public static final List<String> PERFILES_INTERMEDIOS = List.of("SmartOLT", "GUAJIRANET");

    /** En la lista de perfiles de origen, las ONUs que no tienen ningún perfil TR-069. */
    public static final String SIN_PERFIL = "SIN_PERFIL";

    /**
     * @param perfilesOrigen los perfiles desde los que se cambia; {@link #SIN_PERFIL} acepta las ONUs sin perfil
     * @param snEsperado     el SN de la vista previa; si la OLT muestra otro en esa posición, no se toca. Null para no comparar.
     */
    public record Objetivo(String olt, int frame, int slot, int puerto, int ont, List<String> perfilesOrigen, String perfilDestino,
                           boolean guardar, boolean permitirFueraDeLinea, String snEsperado, java.util.function.BooleanSupplier reaplicar) {

        public Objetivo {
            perfilesOrigen = List.copyOf(perfilesOrigen);
        }

        public Objetivo(String olt, int frame, int slot, int puerto, int ont, List<String> perfilesOrigen, String perfilDestino,
                        boolean guardar, boolean permitirFueraDeLinea, String snEsperado) {
            this(olt, frame, slot, puerto, ont, perfilesOrigen, perfilDestino, guardar, permitirFueraDeLinea, snEsperado, null);
        }

        /** Solo se pregunta si la ONU ya tiene el perfil destino. */
        boolean hayQueReaplicar() {
            if (reaplicar == null) {
                return false;
            }
            try {
                return reaplicar.getAsBoolean();
            } catch (RuntimeException e) {
                return false;
            }
        }

        String origenes() {
            return String.join(" o ", perfilesOrigen.stream().map(p -> p.equals(SIN_PERFIL) ? "sin perfil" : p).toList());
        }

        String tarjeta() {
            return frame + "/" + slot;
        }

        String posicion() {
            return olt + " " + frame + "/" + slot + "/" + puerto + " ONT " + ont;
        }
    }

    /** Quién hace el cambio y dentro de qué, para la auditoría. */
    public record Contexto(ActorType actor, Long usuarioId, Long loteId, Long ejecucionId,
                           java.util.function.Consumer<String> alAvanzar) {

        public Contexto(ActorType actor, Long usuarioId, Long loteId, Long ejecucionId) {
            this(actor, usuarioId, loteId, ejecucionId, paso -> { });
        }

        public static Contexto consola() {
            return new Contexto(ActorType.CONSOLA, null, null, null);
        }
    }

    public enum Final {
        COMPLETADA,
        /** La ONU ya tenía el perfil destino: no se hizo nada. */
        YA_TENIA,
        /** No cumplía las condiciones antes de tocarla (fuera de línea, otro SN, otro perfil): no se tocó. */
        EXCLUIDA,
        FALLIDA,
        SIMULADA
    }

    public record Paso(String codigo, String comando, String respuesta, boolean bien, String mensaje) {
    }

    /**
     * @param codigoFallo código del catálogo 8.2 u 8.3 cuando no se completó
     * @param paso        el último paso al que llegó
     */
    public record Resultado(boolean real, Final fin, String mensaje, String sn, String codigoFallo, String paso, List<Paso> pasos) {

        public boolean completado() {
            return fin == Final.COMPLETADA;
        }
    }

    private static final Pattern CAMPO = Pattern.compile("^\\s*[A-Z][\\w ()/\\-]*?\\s+:\\s?.*$");
    private static final Pattern CAMPO_PEGADO = Pattern.compile("\\s{2,}([A-Z][\\w ()/\\-]*?\\s+:\\s?.*)$");

    /** Mientras la OLT guarda la configuración responde "System is busy": se espera y se reintenta. */
    static final java.time.Duration ESPERA_SI_OCUPADA = java.time.Duration.ofSeconds(15);
    static final int REINTENTOS_SI_OCUPADA = 12;
    /** Lo que escribe la OLT cuando termina de guardar. */
    /**
     * Lo que escribe la OLT cuando termina de guardar. Guarda en dos etapas: primero "Configuration file had been saved
     * successfully" y luego la tarjeta de control hasta "...control board is saved completely". Solo la segunda es el final.
     */
    static final java.util.regex.Pattern GUARDADO_TERMINADO = java.util.regex.Pattern.compile("(?i)saved\\s+completely");
    static final java.time.Duration ESPERA_GUARDADO = java.time.Duration.ofMinutes(3);

    private final AuditTrailPort auditoria;
    private final java.util.function.Consumer<java.time.Duration> esperar;

    public CambioDePerfilUnaOnu(AuditTrailPort auditoria) {
        this(auditoria, d -> {
            try {
                Thread.sleep(d.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrumpido esperando a la OLT", e);
            }
        });
    }

    CambioDePerfilUnaOnu(AuditTrailPort auditoria, java.util.function.Consumer<java.time.Duration> esperar) {
        this.auditoria = Objects.requireNonNull(auditoria, "auditoria");
        this.esperar = Objects.requireNonNull(esperar, "esperar");
    }

    /** Manda el comando; si la OLT está ocupada (guardando), espera y lo repite. Devuelve la última respuesta. */
    private String enviar(OltConsolePort consola, String comando) {
        String respuesta = consola.ejecutar(comando);
        for (int i = 0; i < REINTENTOS_SI_OCUPADA && RespuestaHuawei.estaOcupada(respuesta); i++) {
            esperar.accept(ESPERA_SI_OCUPADA);
            respuesta = consola.ejecutar(comando);
        }
        return respuesta;
    }

    public Resultado ejecutar(Objetivo o, boolean real, Supplier<OltConsolePort> abrirConsola) {
        return ejecutar(o, real, Contexto.consola(), abrirConsola);
    }

    public Resultado ejecutar(Objetivo o, boolean real, Contexto ctx, Supplier<OltConsolePort> abrirConsola) {
        Registro r = new Registro(o, ctx);
        String leer = "display ont info " + o.frame() + " " + o.slot() + " " + o.puerto() + " " + o.ont();
        OltConsolePort consola;
        try {
            consola = abrirConsola.get();
        } catch (RuntimeException e) {
            return r.fin(real, Final.FALLIDA, "P02", "FALLO_OLT_SIN_ACCESO", "No se pudo conectar a " + o.olt() + ": " + e.getMessage());
        }
        try (consola) {
            r.paso("P02", "(sesión SSH)", "", true, "Sesión abierta con " + o.olt() + ".");
            consola.ejecutar("enable");

            // Los perfiles por nombre: su ID puede cambiar de una OLT a otra.
            String tabla = enviar(consola, "display ont tr069-server-profile all");
            Optional<TablaDePerfilesHuawei> perfiles = TablaDePerfilesHuawei.leer(tabla);
            Optional<Integer> destino = perfiles.flatMap(t -> t.idDe(o.perfilDestino()));
            boolean aceptaSinPerfil = o.perfilesOrigen().contains(SIN_PERFIL);
            java.util.Set<Integer> origenes = new java.util.TreeSet<>();
            o.perfilesOrigen().stream().filter(n -> !n.equals(SIN_PERFIL))
                    .forEach(n -> perfiles.flatMap(t -> t.idDe(n)).ifPresent(origenes::add));
            if (destino.isEmpty() || (origenes.isEmpty() && !aceptaSinPerfil)) {
                r.paso("P03", "display ont tr069-server-profile all", tabla, false,
                        "En " + o.olt() + " no están los perfiles " + o.origenes() + " y " + o.perfilDestino() + ".");
                return r.fin(real, Final.FALLIDA, "P03", "FALLO_FOTO_PREVIA",
                        "En " + o.olt() + " no se encontró el perfil " + (destino.isEmpty() ? o.perfilDestino() : o.origenes()) + ".");
            }
            r.paso("P03", "display ont tr069-server-profile all", tabla, true,
                    "Se cambia desde " + o.origenes() + " " + origenes + " a " + o.perfilDestino() + " (perfil " + destino.get() + ").");

            // P03: foto previa.
            String foto = enviar(consola, leer);
            Optional<LecturaOntHuawei> antes = LecturaOntHuawei.leer(foto);
            if (antes.isEmpty()) {
                r.paso("P03", leer, foto, false, "No se pudo leer la ONU en la OLT.");
                return r.fin(real, Final.FALLIDA, "P03", "FALLO_FOTO_PREVIA", "No se pudo leer la ONU en " + o.posicion() + ".");
            }
            LecturaOntHuawei a = antes.get();
            r.sn = a.sn();
            String estado = "SN " + a.sn() + ", " + (a.enLinea() ? "en línea" : "fuera de línea") + ", perfil "
                    + a.perfilId().map(String::valueOf).orElse("ninguno") + a.perfilNombre().map(n -> " (" + n + ")").orElse("");
            if (o.snEsperado() != null && !o.snEsperado().equalsIgnoreCase(a.sn())) {
                r.paso("P03", leer, foto, false, "Foto previa: " + estado + ".");
                return r.fin(real, Final.EXCLUIDA, "P03", "EXC_CAMBIO_AL_EJECUTAR", "En " + o.posicion() + " hay otro equipo: se esperaba "
                        + o.snEsperado() + " y la OLT muestra " + a.sn() + ". No se tocó.");
            }
            // Ya tiene el destino, pero si después se le puso la VLAN de gestión hay que reaplicarlo (intermedio y destino
            // otra vez): así la OLT le reenvía la configuración TR-069 y la ONU pide IP por esa VLAN sin reiniciarla.
            Optional<Integer> intermedio = Optional.empty();
            if (a.perfilId().equals(destino)) {
                if (!a.enLinea() || !o.hayQueReaplicar()) {
                    r.paso("P03", leer, foto, true, "Foto previa: " + estado + ".");
                    return r.fin(real, Final.YA_TENIA, "P03", null, "La ONU ya tiene el perfil " + o.perfilDestino() + ": no hay nada que cambiar.");
                }
                intermedio = PERFILES_INTERMEDIOS.stream().map(n -> perfiles.flatMap(t -> t.idDe(n)))
                        .flatMap(Optional::stream).filter(id -> !Optional.of(id).equals(destino)).findFirst();
                if (intermedio.isEmpty()) {
                    r.paso("P03", leer, foto, false, "Foto previa: " + estado + ".");
                    return r.fin(real, Final.FALLIDA, "P03", "FALLO_FOTO_PREVIA", "La ONU tiene la VLAN de gestión y hay que reaplicarle "
                            + o.perfilDestino() + ", pero en " + o.olt() + " no hay otro perfil para pasar por él (" + String.join(" ni ", PERFILES_INTERMEDIOS) + ").");
                }
                r.paso("P03", leer, foto, true, "Foto previa: " + estado + ". Tiene la VLAN de gestión: se reaplica el perfil (perfil "
                        + intermedio.get() + " y luego " + o.perfilDestino() + ").");
            }
            boolean esOrigen = intermedio.isPresent() || a.perfilId().map(origenes::contains).orElse(aceptaSinPerfil);
            if (!esOrigen) {
                r.paso("P03", leer, foto, false, "Foto previa: " + estado + ".");
                return r.fin(real, Final.EXCLUIDA, "P03", "EXC_CAMBIO_AL_EJECUTAR",
                        "La ONU tiene el perfil " + a.perfilId().map(String::valueOf).orElse("ninguno") + " y este lote solo cambia "
                                + o.origenes() + ". No se tocó.");
            }
            if (!a.enLinea() && !o.permitirFueraDeLinea()) {
                r.paso("P03", leer, foto, false, "Foto previa: " + estado + ".");
                return r.fin(real, Final.EXCLUIDA, "P03", "EXC_FUERA_DE_LINEA", "La ONU está fuera de línea: no se toca.");
            }
            if (intermedio.isEmpty()) {
                r.paso("P03", leer, foto, true, "Foto previa: " + estado + ".");
            }

            String asignar = "ont tr069-server-config " + o.puerto() + " " + o.ont() + " profile-id ";
            List<String> cambio = new ArrayList<>(List.of("config", "interface gpon " + o.tarjeta()));
            intermedio.ifPresent(id -> cambio.add(asignar + id));
            cambio.add(asignar + destino.get());
            cambio.add("quit");
            if (!real) {
                List<String> planeados = new ArrayList<>(cambio);
                planeados.add(leer);
                if (o.guardar()) {
                    planeados.add("save");
                }
                planeados.forEach(c -> r.paso("SIM", c, "", true, "Simulación: no se mandó."));
                return r.fin(false, Final.SIMULADA, "P06", null, "Simulación terminada: la ONU cumple las condiciones; no se cambió nada.");
            }

            // P04: entrar a la tarjeta.
            String resp = enviar(consola, "config");
            if (!consola.prompt().contains("(config)")) {
                r.paso("P04", "config", resp, false, "La OLT no entró a modo de configuración.");
                return r.fin(true, Final.FALLIDA, "P04", "FALLO_OLT_RECHAZO", "La OLT no entró a modo de configuración. No se cambió nada.");
            }
            r.paso("P04", "config", resp, true, "En modo de configuración.");
            resp = enviar(consola, cambio.get(1));
            if (!consola.prompt().contains("gpon-" + o.tarjeta())) {
                r.paso("P04", cambio.get(1), resp, false, "La OLT no entró a la tarjeta " + o.tarjeta() + ".");
                salir(consola);
                return r.fin(true, Final.FALLIDA, "P04", "FALLO_OLT_RECHAZO",
                        "La OLT no entró a la tarjeta " + o.tarjeta() + ". No se cambió nada.");
            }
            r.paso("P04", cambio.get(1), resp, true, "Dentro de la tarjeta " + o.tarjeta() + ".");

            // P05/P06: el comando reemplaza el perfil directamente (sin undo). Al reaplicar van dos: intermedio y destino.
            boolean rechazo = false;
            for (String comando : cambio.subList(2, cambio.size() - 1)) {
                resp = enviar(consola, comando);
                rechazo = esError(resp);
                r.paso("P06", comando, resp, !rechazo, rechazo ? "La OLT rechazó el comando." : "La OLT aceptó el comando.");
                if (rechazo) {
                    break;
                }
            }
            r.paso("P06", "quit", consola.ejecutar("quit"), true, "Fuera de la tarjeta.");

            // Verificación: se lee siempre, también si hubo rechazo, para saber cómo quedó.
            String verificacion = enviar(consola, leer);
            Optional<LecturaOntHuawei> despues = LecturaOntHuawei.leer(verificacion);
            boolean quedo = despues.flatMap(LecturaOntHuawei::perfilId).equals(destino);
            String ahora = despues.flatMap(LecturaOntHuawei::perfilId).map(String::valueOf).orElse("ilegible");
            r.paso("P06", leer, verificacion, quedo, quedo ? "Verificado: la ONU quedó con el perfil " + o.perfilDestino() + "."
                    : "Verificación: la ONU tiene el perfil " + ahora + " y se esperaba " + destino.get() + ".");
            if (rechazo || !quedo) {
                salir(consola);
                if (rechazo && RespuestaHuawei.estaOcupada(resp)) {
                    return r.fin(true, Final.FALLIDA, "P06", "FALLO_SISTEMA_NO_RESPONDE",
                            "La OLT siguió ocupada (guardando la configuración) durante 3 minutos y no aceptó el cambio. Perfil actual: "
                                    + ahora + ". Se puede volver a intentar más tarde.");
                }
                return r.fin(true, Final.FALLIDA, "P06", rechazo ? "FALLO_OLT_RECHAZO" : "FALLO_VERIFICACION",
                        (rechazo ? "La OLT rechazó el cambio." : "El cambio no quedó aplicado.") + " Perfil actual: " + ahora + ".");
            }

            // P07: guardar.
            if (o.guardar()) {
                String s = enviar(consola, "save");
                boolean guardo = !esError(s);
                // La OLT sigue guardando en segundo plano después de responder: se espera a que avise que terminó, para
                // que la siguiente ONU no la encuentre ocupada.
                boolean termino = !guardo || GUARDADO_TERMINADO.matcher(s).find()
                        || consola.esperarTexto(GUARDADO_TERMINADO, ESPERA_GUARDADO);
                r.paso("P07", "save", s, guardo, !guardo ? "La OLT no confirmó el guardado."
                        : termino ? "Configuración de la OLT guardada." : "La OLT aceptó el guardado y sigue guardando en segundo plano.");
                if (!guardo) {
                    salir(consola);
                    return r.fin(true, Final.FALLIDA, "P07", "FALLO_NO_GUARDADO",
                            "El cambio está aplicado y verificado, pero la OLT no confirmó que guardó la configuración.");
                }
            }
            consola.ejecutar("quit");
            return r.fin(true, Final.COMPLETADA, o.guardar() ? "P07" : "P06", null, "Cambio completado: " + o.posicion()
                    + (intermedio.isPresent() ? " volvió a recibir " + o.perfilDestino() + " (reaplicado por la VLAN de gestión)"
                    : " pasó de " + a.perfilNombre().orElse("sin perfil") + " a " + o.perfilDestino()) + (o.guardar() ? " y la configuración quedó guardada." : "."));
        } catch (RuntimeException e) {
            return r.fin(real, Final.FALLIDA, r.ultimoPaso, "FALLO_SISTEMA_NO_RESPONDE", "Se interrumpió: " + e.getMessage());
        }
    }

    private static boolean esError(String respuesta) {
        return RespuestaHuawei.esError(respuesta);
    }

    private static void salir(OltConsolePort consola) {
        try {
            if (consola.prompt().contains("-if-")) {
                consola.ejecutar("quit");
            }
            consola.ejecutar("quit");
        } catch (RuntimeException ignorada) {
            // La sesión se cierra igual.
        }
    }

    /** Los pasos de una ejecución y su auditoría. */
    private final class Registro {
        private final Objetivo o;
        private final Contexto ctx;
        private final List<Paso> pasos = new ArrayList<>();
        private String sn;
        private String ultimoPaso = "P02";

        Registro(Objetivo o, Contexto ctx) {
            this.o = o;
            this.ctx = ctx;
            this.sn = o.snEsperado();
        }

        void paso(String codigo, String comando, String respuesta, boolean bien, String mensaje) {
            pasos.add(new Paso(codigo, comando, respuesta, bien, mensaje));
            boolean simulado = codigo.equals("SIM");
            if (!simulado && !codigo.equals(ultimoPaso)) {
                ultimoPaso = codigo;
                // La pantalla en vivo muestra en qué paso va la ONU.
                try {
                    ctx.alAvanzar().accept(codigo);
                } catch (RuntimeException ignorada) {
                    // Que no se pueda mostrar el avance no detiene el cambio.
                }
            }
            Map<String, Object> peticion = new LinkedHashMap<>();
            peticion.put("comando", comando);
            peticion.put("onu", o.posicion());
            auditoria.record(base(simulado ? AuditAction.PASO_SIMULADO : bien ? AuditAction.COMANDO_RESPONDIDO : AuditAction.PASO_FALLIDO)
                    .paso(simulado ? null : codigo).peticion(peticion).respuesta(Map.of("texto", sinDescripcion(respuesta)))
                    .resultado(bien ? "OK" : "FALLO").mensaje(mensaje).build());
        }

        Resultado fin(boolean real, Final fin, String paso, String codigo, String mensaje) {
            AuditAction accion = switch (fin) {
                case COMPLETADA -> AuditAction.ONU_COMPLETADA;
                case FALLIDA -> AuditAction.ONU_FALLIDA;
                case EXCLUIDA, YA_TENIA -> AuditAction.ONU_EXCLUIDA_AL_EJECUTAR;
                case SIMULADA -> AuditAction.PASO_SIMULADO;
            };
            auditoria.record(base(accion).paso(paso).codigo(codigo).peticion(Map.of("onu", o.posicion(), "real", real))
                    .resultado(fin.name()).mensaje(mensaje).build());
            return new Resultado(real, fin, mensaje, sn, codigo, paso, List.copyOf(pasos));
        }

        private AuditEntry.Builder base(AuditAction accion) {
            return AuditEntry.builder(ctx.actor(), accion).usuarioId(ctx.usuarioId()).loteId(ctx.loteId())
                    .ejecucionId(ctx.ejecucionId()).sn(sn).sistema(o.olt());
        }
    }

    /** La descripción de la ONU lleva datos del cliente y puede ocupar varias líneas: no va a la auditoría. */
    static String sinDescripcion(String texto) {
        if (texto == null) {
            return "";
        }
        StringBuilder salida = new StringBuilder();
        boolean ocultando = false;
        for (String linea : texto.split("\n", -1)) {
            if (linea.matches("(?i)\\s*Description\\s*:.*")) {
                salida.append("  Description : (oculta)\n");
                ocultando = true;
                continue;
            }
            if (ocultando) {
                if (CAMPO.matcher(linea).matches() || linea.matches("\\s*-{5,}.*")) {
                    ocultando = false;
                } else {
                    Matcher pegado = CAMPO_PEGADO.matcher(linea);
                    if (pegado.find()) {
                        salida.append("  ").append(pegado.group(1)).append('\n');
                        ocultando = false;
                    }
                    continue;
                }
            }
            salida.append(linea).append('\n');
        }
        return salida.toString().stripTrailing();
    }
}
