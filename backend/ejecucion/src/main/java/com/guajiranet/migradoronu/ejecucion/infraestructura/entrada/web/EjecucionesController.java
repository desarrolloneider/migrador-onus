package com.guajiranet.migradoronu.ejecucion.infraestructura.entrada.web;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.guajiranet.migradoronu.comun.errores.ErrorDeNegocio;
import com.guajiranet.migradoronu.comun.puertos.EventoDeEjecucion;
import com.guajiranet.migradoronu.comun.puertos.UserNamesPort;
import com.guajiranet.migradoronu.comun.seguridad.UsuarioAutenticado;
import com.guajiranet.migradoronu.ejecucion.aplicacion.ConsultarEjecucionesUseCase;
import com.guajiranet.migradoronu.ejecucion.aplicacion.ConsultarEjecucionesUseCase.Detalle;
import com.guajiranet.migradoronu.ejecucion.aplicacion.ConsultarEjecucionesUseCase.PasoVista;
import com.guajiranet.migradoronu.ejecucion.aplicacion.EjecucionVista;

/** ESP-08, ESP-09 y ESP-14: las ONUs de un lote, el detalle de una ONU y las fallidas por revisar. */
@RestController
@RequestMapping("/api/v1")
public class EjecucionesController {

    public record EjecucionDto(long id, long loteId, String loteNumero, boolean simulacion, String sn, String cliente, String olt,
                               String tarjeta, int pon, int onuId, int orden, String estado, String pasoActual, String codigo,
                               String mensaje, Instant iniciadaEn, Instant terminadaEn, Long duracionMs, boolean revisada,
                               List<String> cambiosAplicados, Long resueltaEnLoteId, String resueltaEnLote) {
    }

    public record PasoDto(String paso, String nombre, String estado, Instant iniciadoEn, Instant terminadoEn, Long duracionMs,
                          String comando, String respuestaOlt, String esperado, String encontrado, String codigo, String mensaje) {
    }

    public record EventoDto(Instant hora, String paso, String accion, String descripcion, String comando, String respuesta,
                            String esperado, String encontrado, Long duracionMs) {
    }

    public record FotografiaDto(Instant tomadaEn, String perfilTr069, String textoOlt, Map<String, String> datosSmartOlt) {
    }

    public record RevisionDto(String por, Instant en, String nota) {
    }

    public record DetalleDto(long id, long loteId, String loteNumero, boolean simulacion, String sn, String cliente, String olt,
                             String tarjeta, int pon, int onuId, int orden, String estado, String pasoActual, String codigo,
                             String mensaje, Instant iniciadaEn, Instant terminadaEn, Long duracionMs, boolean revisada,
                             List<String> cambiosAplicados, String ip, String modelo, String contrato, List<PasoDto> pasos,
                             List<EventoDto> lineaDeTiempo, FotografiaDto fotografiaPrevia, String estadoAlFallar,
                             RevisionDto revision) {
    }

    public record RevisarRequest(String nota) {
    }

    /** @param ejecuciones las ejecuciones fallidas a reintentar; vacío o null = todas las fallidas del lote */
    public record ReintentarRequest(List<Long> ejecuciones) {
    }

    public record ReintentoDto(int reintentadas, List<String> sns) {
    }

    private final ConsultarEjecucionesUseCase consultas;
    private final UserNamesPort nombres;
    private final com.guajiranet.migradoronu.ejecucion.puertos.salida.ColaDeEjecucionPort cola;
    private final com.guajiranet.migradoronu.comun.puertos.AuditTrailPort auditoria;

    public EjecucionesController(ConsultarEjecucionesUseCase consultas, UserNamesPort nombres,
                                 com.guajiranet.migradoronu.ejecucion.puertos.salida.ColaDeEjecucionPort cola,
                                 com.guajiranet.migradoronu.comun.puertos.AuditTrailPort auditoria) {
        this.consultas = consultas;
        this.nombres = nombres;
        this.cola = cola;
        this.auditoria = auditoria;
    }

    /** Reintenta las fallidas en el MISMO lote: vuelven a pendientes y el ejecutor las toma de nuevo. */
    @PostMapping("/batches/{loteId}/retry-failed")
    public ReintentoDto reintentar(@PathVariable long loteId, @RequestBody(required = false) ReintentarRequest cuerpo,
                                   @AuthenticationPrincipal UsuarioAutenticado usuario, HttpServletRequest request) {
        List<String> sns = cola.reintentarFallidas(loteId, cuerpo == null ? null : cuerpo.ejecuciones());
        if (sns.isEmpty()) {
            throw ErrorDeNegocio.datosInvalidos("No hay ONUs fallidas para reintentar en ese lote (o ya están en otro lote en curso).");
        }
        auditoria.record(com.guajiranet.migradoronu.comun.puertos.AuditEntry.builder(com.guajiranet.migradoronu.comun.puertos.ActorType.USUARIO,
                        com.guajiranet.migradoronu.comun.puertos.AuditAction.LOTE_REINTENTADO)
                .usuarioId(usuario.id()).ipOrigen(request.getRemoteAddr()).loteId(loteId).sistema("lotes")
                .peticion(Map.of("onus", sns)).resultado("OK")
                .mensaje(usuario.usuario() + " reintentó " + sns.size() + " ONU(s) fallidas en el mismo lote.").build());
        return new ReintentoDto(sns.size(), sns);
    }

    @GetMapping("/batches/{loteId}/runs")
    public List<EjecucionDto> delLote(@PathVariable long loteId) {
        return consultas.delLote(loteId).stream().map(EjecucionesController::dto).toList();
    }

    @GetMapping("/onu-runs/failed")
    public List<EjecucionDto> fallidas() {
        return consultas.fallidasSinRevisar().stream().map(EjecucionesController::dto).toList();
    }

    @GetMapping("/onu-runs/{id}")
    public DetalleDto detalle(@PathVariable long id) {
        return dto(consultas.detalle(id).orElseThrow(() -> ErrorDeNegocio.noEncontrado("No existe esa ejecución.")));
    }

    @PostMapping("/onu-runs/{id}/review")
    public DetalleDto revisar(@PathVariable long id, @RequestBody RevisarRequest cuerpo, @AuthenticationPrincipal UsuarioAutenticado usuario,
                              HttpServletRequest request) {
        consultas.revisar(id, usuario.id(), cuerpo == null ? null : cuerpo.nota(), request.getRemoteAddr());
        return detalle(id);
    }

    private static EjecucionDto dto(EjecucionVista e) {
        return new EjecucionDto(e.id(), e.loteId(), e.loteNumero(), e.simulacion(), e.sn(), e.cliente(), e.olt(), e.tarjeta(), e.pon(),
                e.onuId(), e.orden(), e.estado(), e.pasoActual(), e.codigo(), e.mensaje(), e.iniciadaEn(), e.terminadaEn(),
                e.duracionMs(), e.revisada(), List.of(), e.resueltaEnLoteId(), e.resueltaEnLote());
    }

    private DetalleDto dto(Detalle d) {
        EjecucionVista e = d.ejecucion();
        RevisionDto revision = null;
        if (e.revisada() && e.revisadaPor() != null) {
            Set<Long> ids = new HashSet<>(Set.of(e.revisadaPor()));
            revision = new RevisionDto(nombres.nombres(ids).getOrDefault(e.revisadaPor(), "Usuario " + e.revisadaPor()), e.revisadaEn(),
                    e.notaRevision());
        }
        return new DetalleDto(e.id(), e.loteId(), e.loteNumero(), e.simulacion(), e.sn(), e.cliente(), e.olt(), e.tarjeta(), e.pon(),
                e.onuId(), e.orden(), e.estado(), e.pasoActual(), e.codigo(), e.mensaje(), e.iniciadaEn(), e.terminadaEn(),
                e.duracionMs(), e.revisada(), d.cambiosAplicados(), e.ip(), e.modelo(), e.contrato(),
                d.pasos().stream().map(EjecucionesController::dto).toList(),
                d.lineaDeTiempo().stream().map(EjecucionesController::dto).toList(),
                d.foto() == null ? null : new FotografiaDto(d.foto().tomadaEn(), d.foto().perfilTr069(), d.foto().textoOlt(), Map.of()),
                "FALLIDA".equals(e.estado()) ? e.mensaje() : null, revision);
    }

    private static PasoDto dto(PasoVista p) {
        Long duracion = p.iniciadoEn() == null || p.terminadoEn() == null ? null
                : p.terminadoEn().toEpochMilli() - p.iniciadoEn().toEpochMilli();
        return new PasoDto(p.paso(), p.nombre(), p.estado(), p.iniciadoEn(), p.terminadoEn(), duracion, p.comando(), p.respuestaOlt(),
                null, null, p.codigo(), p.mensaje());
    }

    private static EventoDto dto(EventoDeEjecucion v) {
        return new EventoDto(v.hora(), v.paso(), v.accion(), v.mensaje(), v.comando(), v.respuesta(), null, null, null);
    }
}
