package com.guajiranet.migradoronu.ejecucion.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.guajiranet.migradoronu.comun.errores.ErrorDeNegocio;
import com.guajiranet.migradoronu.comun.puertos.ActorType;
import com.guajiranet.migradoronu.comun.puertos.AuditAction;
import com.guajiranet.migradoronu.comun.puertos.AuditEntry;
import com.guajiranet.migradoronu.comun.puertos.AuditTrailPort;
import com.guajiranet.migradoronu.comun.puertos.EventoDeEjecucion;
import com.guajiranet.migradoronu.comun.puertos.RunTimelinePort;
import com.guajiranet.migradoronu.ejecucion.puertos.salida.LecturasDeEjecucionPort;
import com.guajiranet.migradoronu.procedimientos.dominio.LecturaOntHuawei;
import com.guajiranet.migradoronu.procedimientos.dominio.StepCode;

/**
 * Lo que muestran las pantallas de una ejecución: la lista del lote, el detalle con sus 8 pasos y la línea de tiempo
 * (que sale de la auditoría), y las fallidas por revisar (ESP-08, ESP-09, ESP-14).
 */
public class ConsultarEjecucionesUseCase {

    public record PasoVista(String paso, String nombre, String estado, Instant iniciadoEn, Instant terminadoEn, String comando,
                            String respuestaOlt, String codigo, String mensaje) {
    }

    public record Fotografia(Instant tomadaEn, String perfilTr069, String textoOlt) {
    }

    public record Detalle(EjecucionVista ejecucion, List<PasoVista> pasos, List<EventoDeEjecucion> lineaDeTiempo, Fotografia foto,
                          List<String> cambiosAplicados) {
    }

    /** P05 no se usa: el técnico confirmó que el comando de P06 reemplaza el perfil directamente. */
    private static final String SIN_P05 = "No hace falta: el comando de P06 reemplaza el perfil directamente.";
    private static final Set<String> CAMBIOS = Set.of("P04", "P06", "P07");

    private final LecturasDeEjecucionPort lecturas;
    private final RunTimelinePort lineaDeTiempo;
    private final AuditTrailPort auditoria;
    private final Clock reloj;

    public ConsultarEjecucionesUseCase(LecturasDeEjecucionPort lecturas, RunTimelinePort lineaDeTiempo, AuditTrailPort auditoria,
                                       Clock reloj) {
        this.lecturas = Objects.requireNonNull(lecturas, "lecturas");
        this.lineaDeTiempo = Objects.requireNonNull(lineaDeTiempo, "lineaDeTiempo");
        this.auditoria = Objects.requireNonNull(auditoria, "auditoria");
        this.reloj = Objects.requireNonNull(reloj, "reloj");
    }

    public List<EjecucionVista> delLote(long loteId) {
        return lecturas.delLote(loteId);
    }

    public List<EjecucionVista> fallidasSinRevisar() {
        return lecturas.fallidasSinRevisar();
    }

    /** Las fallidas sin revisar de los lotes de ese usuario (null: de todos, para el administrador). */
    public List<EjecucionVista> fallidasSinRevisar(Long creadoPor) {
        return lecturas.fallidasSinRevisar(creadoPor);
    }

    public Optional<Detalle> detalle(long ejecucionId) {
        return lecturas.porId(ejecucionId).map(e -> {
            List<EventoDeEjecucion> eventos = lineaDeTiempo.deEjecucion(e.id(), e.loteId(), e.sn());
            return new Detalle(e, pasos(e, eventos), eventos, foto(eventos), cambios(e, eventos));
        });
    }

    public EjecucionVista revisar(long ejecucionId, long usuarioId, String nota, String ipOrigen) {
        String texto = nota == null ? "" : nota.strip();
        if (texto.length() < 10) {
            throw ErrorDeNegocio.datosInvalidos("La nota de revisión debe tener al menos 10 caracteres.");
        }
        EjecucionVista e = lecturas.porId(ejecucionId).orElseThrow(() -> ErrorDeNegocio.noEncontrado("No existe esa ejecución."));
        if (!lecturas.marcarRevisada(ejecucionId, usuarioId, texto, reloj.instant())) {
            throw ErrorDeNegocio.conflicto("Solo se revisa una ONU fallida que no se haya revisado antes.");
        }
        auditoria.record(AuditEntry.builder(ActorType.USUARIO, AuditAction.ONU_REVISADA).usuarioId(usuarioId).ipOrigen(ipOrigen)
                .loteId(e.loteId()).ejecucionId(e.id()).sn(e.sn()).sistema(e.olt()).peticion(Map.of("nota", texto))
                .resultado("OK").mensaje("Fallida de " + e.sn() + " revisada: " + texto).build());
        return lecturas.porId(ejecucionId).orElseThrow();
    }

    /** "Marcar todas como revisadas" en la pantalla Fallidas: una sola nota para todas. Devuelve cuántas se marcaron. */
    public int revisarTodas(long usuarioId, String nota, String ipOrigen) {
        return revisarTodas(usuarioId, nota, ipOrigen, null);
    }

    /** Con {@code creadoPor}, solo las de los lotes de ese usuario. */
    public int revisarTodas(long usuarioId, String nota, String ipOrigen, Long creadoPor) {
        String texto = nota == null || nota.isBlank() ? "Marcadas como revisadas todas a la vez desde la pantalla Fallidas." : nota.strip();
        if (texto.length() < 10) {
            throw ErrorDeNegocio.datosInvalidos("La nota de revisión debe tener al menos 10 caracteres.");
        }
        int marcadas = lecturas.marcarTodasRevisadas(usuarioId, texto, reloj.instant(), creadoPor);
        auditoria.record(AuditEntry.builder(ActorType.USUARIO, AuditAction.ONU_REVISADA).usuarioId(usuarioId).ipOrigen(ipOrigen)
                .peticion(Map.of("nota", texto, "todas", true)).resultado("OK")
                .mensaje(marcadas + " fallida(s) marcadas como revisadas a la vez: " + texto).build());
        return marcadas;
    }

    private static List<PasoVista> pasos(EjecucionVista e, List<EventoDeEjecucion> eventos) {
        boolean terminada = e.terminadaEn() != null;
        List<PasoVista> pasos = new ArrayList<>();
        for (StepCode codigo : StepCode.values()) {
            String c = codigo.name();
            List<EventoDeEjecucion> delPaso = eventos.stream()
                    .filter(v -> c.equals(v.paso()) && (v.accion().equals("COMANDO_RESPONDIDO") || v.accion().equals("PASO_FALLIDO")))
                    .toList();
            Optional<EventoDeEjecucion> fallo = delPaso.stream().filter(v -> v.accion().equals("PASO_FALLIDO")).findFirst();
            Optional<EventoDeEjecucion> ultimo = delPaso.isEmpty() ? Optional.empty() : Optional.of(delPaso.getLast());
            Instant inicio = delPaso.isEmpty() ? null : delPaso.getFirst().hora();
            Instant fin = ultimo.map(EventoDeEjecucion::hora).orElse(null);
            String estado;
            String mensaje = ultimo.map(EventoDeEjecucion::mensaje).orElse(null);
            if (fallo.isPresent()) {
                estado = "FALLIDO";
                mensaje = fallo.get().mensaje();
            } else if (!delPaso.isEmpty()) {
                estado = "CORRECTO";
            } else if (c.equals("P05")) {
                estado = "NO_EJECUTADO";
                mensaje = SIN_P05;
            } else if (c.equals("P01")) {
                estado = terminada || e.pasoActual() != null ? "NO_EJECUTADO" : "PENDIENTE";
                mensaje = "La revalidación se hace en la OLT: la foto previa (P03) compara el SN y el perfil antes de tocar nada.";
            } else if (e.simulacion() && "COMPLETADA".equals(e.estado()) && CAMBIOS.contains(c)) {
                estado = "SIMULADO";
                mensaje = "Simulación: no se mandó.";
            } else if (c.equals("P08") && terminada) {
                estado = "CORRECTO";
                mensaje = "Sesión cerrada.";
            } else if (!terminada && c.equals(e.pasoActual())) {
                estado = "EN_CURSO";
            } else {
                estado = terminada ? "NO_EJECUTADO" : "PENDIENTE";
            }
            pasos.add(new PasoVista(c, codigo.displayName(), estado, inicio, fin, ultimo.map(EventoDeEjecucion::comando).orElse(null),
                    ultimo.map(EventoDeEjecucion::respuesta).orElse(null), fallo.map(EventoDeEjecucion::codigo).orElse(null), mensaje));
        }
        return pasos;
    }

    private static Fotografia foto(List<EventoDeEjecucion> eventos) {
        return eventos.stream()
                .filter(v -> "P03".equals(v.paso()) && v.comando() != null && v.comando().startsWith("display ont info"))
                .findFirst()
                .map(v -> new Fotografia(v.hora(),
                        LecturaOntHuawei.leer(v.respuesta()).flatMap(LecturaOntHuawei::perfilNombre).orElse(null),
                        v.respuesta()))
                .orElse(null);
    }

    private static List<String> cambios(EjecucionVista e, List<EventoDeEjecucion> eventos) {
        if (e.simulacion()) {
            return List.of();
        }
        return eventos.stream()
                .filter(v -> v.accion().equals("COMANDO_RESPONDIDO") && v.comando() != null
                        && (v.comando().startsWith("ont tr069-server-config") || v.comando().equals("save")))
                .map(EventoDeEjecucion::comando).toList();
    }
}
