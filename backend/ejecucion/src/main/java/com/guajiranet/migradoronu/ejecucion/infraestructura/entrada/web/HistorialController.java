package com.guajiranet.migradoronu.ejecucion.infraestructura.entrada.web;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.guajiranet.migradoronu.comun.errores.ErrorDeNegocio;
import com.guajiranet.migradoronu.comun.puertos.ActorType;
import com.guajiranet.migradoronu.comun.puertos.AuditAction;
import com.guajiranet.migradoronu.comun.puertos.AuditEntry;
import com.guajiranet.migradoronu.comun.puertos.AuditTrailPort;
import com.guajiranet.migradoronu.comun.seguridad.UsuarioAutenticado;
import com.guajiranet.migradoronu.ejecucion.aplicacion.HistorialDeOnus.Entrada;
import com.guajiranet.migradoronu.ejecucion.aplicacion.HistorialDeOnus.Filtros;
import com.guajiranet.migradoronu.ejecucion.aplicacion.HistorialDeOnus.Intento;
import com.guajiranet.migradoronu.ejecucion.aplicacion.HistorialDeOnus.Pagina;
import com.guajiranet.migradoronu.ejecucion.puertos.salida.HistorialDeOnusPort;

/** ESP-14: el último resultado de cada ONU, sus intentos y la exportación. */
@RestController
@RequestMapping("/api/v1/onu-history")
public class HistorialController {

    private static final ZoneId ZONA = ZoneId.of("America/Bogota");

    private final HistorialDeOnusPort historial;
    private final AuditTrailPort auditoria;

    public HistorialController(HistorialDeOnusPort historial, AuditTrailPort auditoria) {
        this.historial = historial;
        this.auditoria = auditoria;
    }

    @GetMapping
    public Pagina buscar(@RequestParam(required = false) String tipo, @RequestParam(required = false) String sn, @RequestParam(required = false) String olt,
                         @RequestParam(required = false) String tarjeta, @RequestParam(required = false) String resultado,
                         @RequestParam(required = false) String desde, @RequestParam(required = false) String hasta,
                         @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "25") int tamano) {
        return historial.buscar(filtros(tipo, sn, olt, tarjeta, resultado, desde, hasta, pagina, Math.min(Math.max(tamano, 1), 500)));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportar(@RequestParam(required = false) String tipo, @RequestParam(required = false) String sn, @RequestParam(required = false) String olt,
                                           @RequestParam(required = false) String tarjeta, @RequestParam(required = false) String resultado,
                                           @RequestParam(required = false) String desde, @RequestParam(required = false) String hasta,
                                           @AuthenticationPrincipal UsuarioAutenticado quien, HttpServletRequest request) {
        List<Entrada> entradas = historial.buscar(filtros(tipo, sn, olt, tarjeta, resultado, desde, hasta, 0, 50_000)).entradas();
        String csv = Stream.concat(Stream.of("sn;ip;olt;tarjeta;pon;onu_id;resultado;revisada;codigo;motivo;lote;fecha"),
                        entradas.stream().map(e -> Stream.of(e.sn(), e.ip(), e.olt(), e.tarjeta(), texto(e.pon()), texto(e.onuId()),
                                e.ultimoResultado(), texto(e.revisada()), e.codigo(), e.motivo(), e.loteNumero(), texto(e.actualizadoEn()))
                                .map(HistorialController::celda).collect(Collectors.joining(";"))))
                .collect(Collectors.joining("\r\n", "", "\r\n"));
        auditoria.record(AuditEntry.builder(ActorType.USUARIO, AuditAction.HISTORIAL_EXPORTADO).usuarioId(quien.id())
                .ipOrigen(request.getRemoteAddr()).sistema("historial").peticion(Map.of("onus", entradas.size())).resultado("OK")
                .mensaje(quien.usuario() + " exportó el historial de " + entradas.size() + " ONUs.").build());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"historial-onus.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(("﻿" + csv).getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/{sn}")
    public List<Intento> intentos(@PathVariable String sn) {
        return historial.intentos(sn.strip().toUpperCase());
    }

    private static Filtros filtros(String tipo, String sn, String olt, String tarjeta, String resultado, String desde, String hasta, int pagina, int tamano) {
        String paso = limpio(tipo);
        if (paso != null && !List.of("VLAN_GESTION", "PERFIL_TR069", "SERVICIO_WAN", "TRES_PASOS").contains(paso)) {
            throw ErrorDeNegocio.datosInvalidos("Paso inválido: " + paso + ".");
        }
        return new Filtros(paso, limpio(sn), limpio(olt), limpio(tarjeta), limpio(resultado), fecha(desde, false), fecha(hasta, true),
                Math.max(pagina, 0), tamano);
    }

    private static Instant fecha(String valor, boolean finDelDia) {
        String v = limpio(valor);
        if (v == null) {
            return null;
        }
        try {
            LocalDate d = LocalDate.parse(v.length() > 10 ? v.substring(0, 10) : v);
            return (finDelDia ? d.plusDays(1).atStartOfDay(ZONA).minusNanos(1_000) : d.atStartOfDay(ZONA)).toInstant();
        } catch (DateTimeParseException e) {
            throw ErrorDeNegocio.datosInvalidos("Fecha inválida: " + v + ". Usa el formato 2026-10-07.");
        }
    }

    private static String limpio(String v) {
        return v == null || v.isBlank() ? null : v.strip();
    }

    private static String texto(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static String celda(String valor) {
        if (valor == null) {
            return "";
        }
        String v = !valor.isEmpty() && "=+-@".indexOf(valor.charAt(0)) >= 0 ? "'" + valor : valor;
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
