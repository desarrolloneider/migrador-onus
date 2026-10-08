package com.guajiranet.migradoronu.auditoria.infraestructura.entrada.web;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.guajiranet.migradoronu.auditoria.aplicacion.AuditPage;
import com.guajiranet.migradoronu.auditoria.aplicacion.AuditSearchCriteria;
import com.guajiranet.migradoronu.auditoria.aplicacion.VerifyAuditIntegrityCommand;
import com.guajiranet.migradoronu.auditoria.dominio.AuditEvent;
import com.guajiranet.migradoronu.auditoria.dominio.IntegrityReport;
import com.guajiranet.migradoronu.auditoria.dominio.StoredAuditEvent;
import com.guajiranet.migradoronu.auditoria.puertos.entrada.QueryAuditPort;
import com.guajiranet.migradoronu.auditoria.puertos.entrada.VerifyAuditIntegrityPort;
import com.guajiranet.migradoronu.comun.errores.ErrorDeNegocio;
import com.guajiranet.migradoronu.comun.puertos.ActorType;
import com.guajiranet.migradoronu.comun.puertos.AuditAction;
import com.guajiranet.migradoronu.comun.puertos.AuditEntry;
import com.guajiranet.migradoronu.comun.puertos.AuditTrailPort;
import com.guajiranet.migradoronu.comun.puertos.BatchNumbersPort;
import com.guajiranet.migradoronu.comun.puertos.UserNamesPort;
import com.guajiranet.migradoronu.comun.seguridad.UsuarioAutenticado;

import tools.jackson.databind.json.JsonMapper;

/** ESP-13: consultar, exportar y verificar la auditoría. Solo lectura, salvo el registro de la propia consulta. */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditoriaController {

    public record EventoDto(long id, Instant ocurridoEn, String actorTipo, String usuario, String ipOrigen, String accion, Long loteId,
                            String loteNumero, String sn, String paso, String sistema, Map<String, Object> peticion,
                            Map<String, Object> respuesta, String resultado, String codigo, String mensaje, Long duracionMs) {
    }

    public record PaginaDto(List<EventoDto> eventos, long total, int pagina, int tamano) {
    }

    public record IntegridadDto(boolean intacta, long eventosRevisados, Long primerEventoRoto, String motivo, String mensaje) {
    }

    private static final ZoneId ZONA = ZoneId.of("America/Bogota");
    private static final int MAXIMO_EXPORTACION = 50_000;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final QueryAuditPort consultas;
    private final VerifyAuditIntegrityPort integridad;
    private final UserNamesPort usuarios;
    private final BatchNumbersPort lotes;
    private final AuditTrailPort auditoria;

    public AuditoriaController(QueryAuditPort consultas, VerifyAuditIntegrityPort integridad, UserNamesPort usuarios,
                               BatchNumbersPort lotes, AuditTrailPort auditoria) {
        this.consultas = consultas;
        this.integridad = integridad;
        this.usuarios = usuarios;
        this.lotes = lotes;
        this.auditoria = auditoria;
    }

    @GetMapping
    public PaginaDto buscar(@RequestParam(required = false) String desde, @RequestParam(required = false) String hasta,
                            @RequestParam(required = false) String usuario, @RequestParam(required = false) String lote,
                            @RequestParam(required = false) String sn, @RequestParam(required = false) String sistema,
                            @RequestParam(required = false) String paso, @RequestParam(required = false) String accion,
                            @RequestParam(required = false) String resultado, @RequestParam(defaultValue = "0") int pagina,
                            @RequestParam(defaultValue = "50") int tamano) {
        Optional<AuditSearchCriteria> criterio = criterio(desde, hasta, usuario, lote, sn, sistema, paso, accion, resultado, pagina,
                Math.min(Math.max(tamano, 1), AuditSearchCriteria.MAX_PAGE_SIZE));
        if (criterio.isEmpty()) {
            return new PaginaDto(List.of(), 0, pagina, tamano);
        }
        AuditPage resultadoPagina = consultas.search(criterio.get());
        return new PaginaDto(dtos(resultadoPagina.events()), resultadoPagina.total(), resultadoPagina.page(), resultadoPagina.size());
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportar(@RequestParam(required = false) String desde, @RequestParam(required = false) String hasta,
                                           @RequestParam(required = false) String usuario, @RequestParam(required = false) String lote,
                                           @RequestParam(required = false) String sn, @RequestParam(required = false) String sistema,
                                           @RequestParam(required = false) String paso, @RequestParam(required = false) String accion,
                                           @RequestParam(required = false) String resultado,
                                           @AuthenticationPrincipal UsuarioAutenticado quien, HttpServletRequest request) {
        List<EventoDto> eventos = new ArrayList<>();
        int pagina = 0;
        while (eventos.size() < MAXIMO_EXPORTACION) {
            Optional<AuditSearchCriteria> c = criterio(desde, hasta, usuario, lote, sn, sistema, paso, accion, resultado, pagina++,
                    AuditSearchCriteria.MAX_PAGE_SIZE);
            if (c.isEmpty()) {
                break;
            }
            AuditPage p = consultas.search(c.get());
            eventos.addAll(dtos(p.events()));
            if (p.events().size() < AuditSearchCriteria.MAX_PAGE_SIZE) {
                break;
            }
        }
        String csv = Stream.concat(
                        Stream.of("id;ocurrido_en;actor;usuario;ip;accion;lote;sn;paso;sistema;resultado;codigo;mensaje;peticion;respuesta"),
                        eventos.stream().map(e -> Stream.of(String.valueOf(e.id()), String.valueOf(e.ocurridoEn()), e.actorTipo(), e.usuario(),
                                        e.ipOrigen(), e.accion(), e.loteNumero(), e.sn(), e.paso(), e.sistema(), e.resultado(), e.codigo(), e.mensaje(),
                                        JSON.writeValueAsString(e.peticion()), JSON.writeValueAsString(e.respuesta()))
                                .map(AuditoriaController::celda).collect(Collectors.joining(";"))))
                .collect(Collectors.joining("\r\n", "", "\r\n"));
        auditoria.record(AuditEntry.builder(ActorType.USUARIO, AuditAction.AUDITORIA_EXPORTADA)
                .usuarioId(quien.id()).ipOrigen(request.getRemoteAddr()).sistema("auditoria")
                .peticion(Map.of("eventos", eventos.size())).resultado("OK")
                .mensaje(quien.usuario() + " exportó " + eventos.size() + " eventos de la auditoría.").build());
        byte[] datos = ("﻿" + csv).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"auditoria.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(datos);
    }

    @PostMapping("/verify")
    public IntegridadDto verificar(@AuthenticationPrincipal UsuarioAutenticado quien, HttpServletRequest request) {
        IntegrityReport r = integridad.verify(new VerifyAuditIntegrityCommand(ActorType.USUARIO, quien.id(), request.getRemoteAddr()));
        return new IntegridadDto(r.intact(), r.checkedEvents(), r.brokenEventId(), r.reason(), r.message());
    }

    /** Vacío si un filtro por nombre no coincide con nada: la respuesta es una página vacía, no un error. */
    private Optional<AuditSearchCriteria> criterio(String desde, String hasta, String usuario, String lote, String sn, String sistema,
                                                   String paso, String accion, String resultado, int pagina, int tamano) {
        Long usuarioId = null;
        if (texto(usuario) != null) {
            Set<Long> ids = usuarios.idsQueCoinciden(usuario.strip());
            if (ids.isEmpty()) {
                return Optional.empty();
            }
            if (ids.size() > 1) {
                throw ErrorDeNegocio.datosInvalidos("Hay " + ids.size() + " usuarios que coinciden con \"" + usuario.strip()
                        + "\". Escribe el usuario completo.");
            }
            usuarioId = ids.iterator().next();
        }
        Long loteId = null;
        if (texto(lote) != null) {
            String l = lote.strip();
            Optional<Long> id = l.matches("\\d+") ? Optional.of(Long.valueOf(l)) : lotes.idDe(l);
            if (id.isEmpty()) {
                return Optional.empty();
            }
            loteId = id.get();
        }
        AuditAction accionFiltro = null;
        if (texto(accion) != null) {
            try {
                accionFiltro = AuditAction.valueOf(accion.strip());
            } catch (IllegalArgumentException e) {
                throw ErrorDeNegocio.datosInvalidos("No existe la acción " + accion.strip() + ".");
            }
        }
        return Optional.of(new AuditSearchCriteria(instante(desde, false), instante(hasta, true), usuarioId, loteId, texto(sn),
                texto(sistema), texto(paso), texto(resultado), accionFiltro, Math.max(pagina, 0), tamano, true));
    }

    private List<EventoDto> dtos(List<StoredAuditEvent> eventos) {
        Set<Long> idsUsuario = new HashSet<>();
        Set<Long> idsLote = new HashSet<>();
        eventos.stream().filter(StoredAuditEvent::isReadable).map(StoredAuditEvent::event).forEach(e -> {
            if (e.usuarioId() != null) {
                idsUsuario.add(e.usuarioId());
            }
            if (e.loteId() != null) {
                idsLote.add(e.loteId());
            }
        });
        Map<Long, String> nombres = idsUsuario.isEmpty() ? Map.of() : usuarios.nombres(idsUsuario);
        Map<Long, String> numeros = idsLote.isEmpty() ? Map.of() : lotes.numeros(idsLote);
        return eventos.stream().map(s -> {
            if (!s.isReadable()) {
                return new EventoDto(s.id(), null, null, null, null, "ILEGIBLE", null, null, null, null, null, Map.of(), Map.of(),
                        "ILEGIBLE", null, "Este evento no se pudo leer: " + s.unreadableReason(), null);
            }
            AuditEvent e = s.event();
            return new EventoDto(s.id(), e.ocurridoEn(), e.actorTipo().name(),
                    e.usuarioId() == null ? null : nombres.getOrDefault(e.usuarioId(), "Usuario " + e.usuarioId()), e.ipOrigen(),
                    e.accion().name(), e.loteId(), e.loteId() == null ? null : numeros.get(e.loteId()), e.sn(), e.paso(), e.sistema(),
                    Objects.requireNonNullElse(e.peticion(), Map.of()), Objects.requireNonNullElse(e.respuesta(), Map.of()),
                    e.resultado(), e.codigo(), e.mensaje(), e.duracionMs());
        }).toList();
    }

    /** Acepta una fecha (2026-10-07), una fecha y hora local o un instante ISO. Las fechas van en hora de Colombia. */
    private static Instant instante(String valor, boolean finDelDia) {
        String v = texto(valor);
        if (v == null) {
            return null;
        }
        try {
            return Instant.parse(v);
        } catch (DateTimeParseException ignorada) {
            // Se prueba el siguiente formato.
        }
        try {
            return LocalDateTime.parse(v).atZone(ZONA).toInstant();
        } catch (DateTimeParseException ignorada) {
            // Se prueba el siguiente formato.
        }
        try {
            LocalDate fecha = LocalDate.parse(v.length() > 10 ? v.substring(0, 10) : v);
            return (finDelDia ? fecha.plusDays(1).atStartOfDay(ZONA).minusNanos(1_000) : fecha.atStartOfDay(ZONA)).toInstant();
        } catch (DateTimeParseException e) {
            throw ErrorDeNegocio.datosInvalidos("Fecha inválida: " + v + ". Usa el formato 2026-10-07.");
        }
    }

    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }

    private static String celda(String valor) {
        if (valor == null) {
            return "";
        }
        String v = valor;
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
