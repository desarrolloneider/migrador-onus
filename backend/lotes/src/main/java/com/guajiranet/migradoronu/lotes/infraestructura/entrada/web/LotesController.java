package com.guajiranet.migradoronu.lotes.infraestructura.entrada.web;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.guajiranet.migradoronu.comun.errores.ErrorDeNegocio;
import com.guajiranet.migradoronu.comun.puertos.UserNamesPort;
import com.guajiranet.migradoronu.comun.seguridad.UsuarioAutenticado;
import com.guajiranet.migradoronu.lotes.aplicacion.BatchView;
import com.guajiranet.migradoronu.lotes.aplicacion.BuildBatchCommand;
import com.guajiranet.migradoronu.lotes.aplicacion.BuildSingleOnuPreviewCommand;
import com.guajiranet.migradoronu.lotes.aplicacion.ConfirmBatchCommand;
import com.guajiranet.migradoronu.lotes.aplicacion.DiscardBatchCommand;
import com.guajiranet.migradoronu.lotes.aplicacion.LoteDetalle;
import com.guajiranet.migradoronu.lotes.aplicacion.StopBatchCommand;
import com.guajiranet.migradoronu.lotes.dominio.BatchFilters;
import com.guajiranet.migradoronu.lotes.dominio.ClassifiedOnu;
import com.guajiranet.migradoronu.lotes.puertos.entrada.BuildBatchPort;
import com.guajiranet.migradoronu.lotes.puertos.entrada.ControlBatchPort;
import com.guajiranet.migradoronu.lotes.puertos.entrada.QueryBatchDetailsPort;

/** ESP-03 a ESP-05, ESP-10 a ESP-12: lotes y pruebas individuales. */
@RestController
@RequestMapping("/api/v1/batches")
public class LotesController {

    public record FiltrosDto(String marca, String olt, String tarjeta, Integer pon, String tipo) {
    }

    public record LoteDto(long id, String numero, String modo, boolean simulacion, String estado, FiltrosDto filtros, String creadoPor,
                          Instant creadoEn, Instant venceEn, String confirmadoPor, Instant confirmadoEn, Instant iniciadoEn,
                          Instant terminadoEn, String motivoDetencion, String procedimientoVersion, Map<String, Long> contadores,
                          long tiempoEstimadoMaximoMin) {
    }

    public record DuplicadoDto(String olt, String tarjeta, int pon, int onuId, boolean enLinea) {
    }

    public record ItemDto(String sn, String ip, String grupo, String codigoMotivo, String mensaje, String cliente, String contrato,
                          String modelo, String olt, String tarjeta, Integer pon, Integer onuId, String perfilActual,
                          List<DuplicadoDto> registrosDuplicados) {
    }

    public record VistaPreviaDto(LoteDto lote, List<ItemDto> items, boolean soloSimulacion, String motivoSoloSimulacion) {
    }

    public record ArmarLoteRequest(String marca, String olt, String tarjeta, Integer pon, List<String> perfiles, List<String> sns, String tipo) {
    }

    public record ArmarPruebaIndividualRequest(String sn) {
    }

    public record ConfirmarLoteRequest(boolean simulacion) {
    }

    private final BuildBatchPort armar;
    private final ControlBatchPort controlar;
    private final QueryBatchDetailsPort consultas;
    private final UserNamesPort nombres;

    public LotesController(BuildBatchPort armar, ControlBatchPort controlar, QueryBatchDetailsPort consultas, UserNamesPort nombres) {
        this.armar = armar;
        this.controlar = controlar;
        this.consultas = consultas;
        this.nombres = nombres;
    }

    @GetMapping
    public List<LoteDto> recientes(@RequestParam(defaultValue = "20") int limit) {
        List<LoteDetalle> lotes = consultas.recientes(limit);
        Map<Long, String> quien = nombresDe(lotes);
        return lotes.stream().map(l -> dto(l, quien)).toList();
    }

    @GetMapping("/{id}")
    public LoteDto consultar(@PathVariable long id) {
        LoteDetalle lote = lote(id);
        return dto(lote, nombresDe(List.of(lote)));
    }

    @PostMapping("/preview")
    public VistaPreviaDto armar(@RequestBody ArmarLoteRequest cuerpo, @AuthenticationPrincipal UsuarioAutenticado usuario) {
        BatchFilters filtros = new BatchFilters(texto(cuerpo.marca()), texto(cuerpo.olt()), texto(cuerpo.tarjeta()), cuerpo.pon(),
                cuerpo.perfiles(), cuerpo.sns(), cuerpo.tipo());
        BatchView lote = armar.buildPreview(new BuildBatchCommand(usuario.id(), filtros));
        return vistaPrevia(lote.id());
    }

    @PostMapping("/single-preview")
    public VistaPreviaDto armarIndividual(@RequestBody ArmarPruebaIndividualRequest cuerpo,
                                          @AuthenticationPrincipal UsuarioAutenticado usuario) {
        BatchView lote = armar.buildSingleOnuPreview(new BuildSingleOnuPreviewCommand(usuario.id(), cuerpo.sn()));
        return vistaPrevia(lote.id());
    }

    @GetMapping("/{id}/preview")
    public VistaPreviaDto vistaPrevia(@PathVariable long id) {
        LoteDetalle lote = lote(id);
        String motivo = consultas.motivoSoloSimulacion().orElse(null);
        List<ItemDto> items = consultas.vistaPrevia(id).stream().map(LotesController::dto).toList();
        return new VistaPreviaDto(dto(lote, nombresDe(List.of(lote))), items, motivo != null, motivo);
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> exportar(@PathVariable long id) {
        LoteDetalle lote = lote(id);
        String csv = Stream.concat(
                Stream.of("sn;ip;grupo;codigo_motivo;mensaje;cliente;contrato;modelo;olt;tarjeta;pon;onu_id;perfil_actual"),
                consultas.vistaPrevia(id).stream().map(c -> Stream.of(c.sn(), c.ip(), c.grupo().name(), c.codigoMotivo(), c.mensaje(),
                        c.cliente(), c.contrato(), c.modelo(), c.oltCodigo(), c.tarjeta(), c.tarjeta() == null ? null : String.valueOf(c.pon()),
                        c.tarjeta() == null ? null : String.valueOf(c.onuId()), c.perfilActual())
                        .map(LotesController::celda).collect(Collectors.joining(";"))))
                .collect(Collectors.joining("\r\n", "", "\r\n"));
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] datos = csv.getBytes(StandardCharsets.UTF_8);
        byte[] salida = new byte[bom.length + datos.length];
        System.arraycopy(bom, 0, salida, 0, bom.length);
        System.arraycopy(datos, 0, salida, bom.length, datos.length);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + lote.numero() + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(salida);
    }

    @PostMapping("/{id}/confirm")
    public LoteDto confirmar(@PathVariable long id, @RequestBody(required = false) ConfirmarLoteRequest cuerpo,
                             @RequestHeader(value = "Idempotency-Key", required = false) String clave,
                             @AuthenticationPrincipal UsuarioAutenticado usuario) {
        boolean simulacion = cuerpo == null || cuerpo.simulacion();
        controlar.confirm(new ConfirmBatchCommand(id, usuario.id(), simulacion, exigirClave(clave)));
        return consultar(id);
    }

    /** Desde una simulación terminada: arma una vista previa nueva con los mismos filtros, para ejecutar de verdad. */
    @PostMapping("/{id}/repeat-real")
    public VistaPreviaDto repetirDeVerdad(@PathVariable long id, @AuthenticationPrincipal UsuarioAutenticado usuario) {
        BatchView nuevo = armar.rebuildFromSimulation(id, usuario.id());
        return vistaPrevia(nuevo.id());
    }

    @PostMapping("/{id}/discard")
    public LoteDto descartar(@PathVariable long id, @RequestHeader(value = "Idempotency-Key", required = false) String clave,
                             @AuthenticationPrincipal UsuarioAutenticado usuario) {
        controlar.discard(new DiscardBatchCommand(id, usuario.id(), exigirClave(clave)));
        return consultar(id);
    }

    @PostMapping("/{id}/stop")
    public LoteDto detener(@PathVariable long id, @RequestHeader(value = "Idempotency-Key", required = false) String clave,
                           @AuthenticationPrincipal UsuarioAutenticado usuario) {
        controlar.stop(new StopBatchCommand(id, usuario.id(), exigirClave(clave)));
        return consultar(id);
    }

    private LoteDetalle lote(long id) {
        return consultas.detalle(id).orElseThrow(() -> ErrorDeNegocio.noEncontrado("No existe el lote " + id + "."));
    }

    private Map<Long, String> nombresDe(List<LoteDetalle> lotes) {
        Set<Long> ids = new HashSet<>();
        lotes.forEach(l -> {
            ids.add(l.creadoPor());
            if (l.confirmadoPor() != null) {
                ids.add(l.confirmadoPor());
            }
        });
        return ids.isEmpty() ? Map.of() : nombres.nombres(ids);
    }

    private LoteDto dto(LoteDetalle l, Map<Long, String> quien) {
        BatchFilters f = l.filtros();
        long entran = l.contadores().getOrDefault("entran", 0L);
        return new LoteDto(l.id(), l.numero(), l.modo().name(), l.simulacion(), l.estado().name(),
                new FiltrosDto(f.marca(), f.oltCodigo(), f.tarjeta(), f.pon(), f.tipo()),
                quien.getOrDefault(l.creadoPor(), "Usuario " + l.creadoPor()), l.creadoEn(), l.venceEn(),
                l.confirmadoPor() == null ? null : quien.getOrDefault(l.confirmadoPor(), "Usuario " + l.confirmadoPor()),
                l.confirmadoEn(), l.iniciadoEn(), l.terminadoEn(), l.motivoDetencion(), l.procedimientoVersion(), l.contadores(),
                entran * consultas.minutosEstimadosPorOnu());
    }

    private static ItemDto dto(ClassifiedOnu c) {
        boolean conPosicion = c.tarjeta() != null;
        return new ItemDto(c.sn(), c.ip(), c.grupo().name(), c.codigoMotivo(), c.mensaje(), c.cliente(), c.contrato(), c.modelo(),
                c.oltCodigo(), c.tarjeta(), conPosicion ? c.pon() : null, conPosicion ? c.onuId() : null, c.perfilActual(),
                c.registrosDuplicados().stream().map(d -> new DuplicadoDto(d.oltCodigo(), d.tarjeta(), d.pon(), d.onuId(), d.enLinea())).toList());
    }

    private static String exigirClave(String clave) {
        if (clave == null || clave.isBlank()) {
            throw ErrorDeNegocio.datosInvalidos("Falta la cabecera Idempotency-Key (RN-24).");
        }
        return clave;
    }

    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }

    /** Celda CSV segura: entre comillas y sin que una hoja de cálculo la tome como fórmula. */
    private static String celda(String valor) {
        if (valor == null) {
            return "";
        }
        String v = Objects.requireNonNullElse(valor, "");
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
