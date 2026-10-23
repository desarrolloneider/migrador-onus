package com.guajiranet.migradoronu.web;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.guajiranet.migradoronu.comun.puertos.ActorType;
import com.guajiranet.migradoronu.comun.puertos.AuditAction;
import com.guajiranet.migradoronu.comun.puertos.AuditEntry;
import com.guajiranet.migradoronu.comun.puertos.AuditTrailPort;
import com.guajiranet.migradoronu.comun.seguridad.UsuarioAutenticado;
import com.guajiranet.migradoronu.ejecucion.puertos.salida.GestionSmartOltPort;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Solo administradores (ReglasDePermiso: /api/v1/vlan-gestion): a las ONUs escogidas que no tienen External ID en
 * SmartOLT les pone uno igual a su SN como lo muestra SmartOLT (HWTCFCA40DB0, sin guion). Sin él SmartOLT no les acepta
 * ningún cambio. De a una, con pausa, en un hilo aparte; cada ONU queda en la auditoría. No toca nada más.
 */
@RestController
@RequestMapping("/api/v1/vlan-gestion/external-id")
public class ExternalIdController {

    private static final int MAXIMO = 1000;

    public record Pedido(List<String> sns) {
    }

    public record Error(String sn, String motivo) {
    }

    public record Estado(boolean enCurso, int total, int hechas, List<Error> errores, Instant iniciadoEn, Instant terminadoEn) {
    }

    private final GestionSmartOltPort smartOlt;
    private final AuditTrailPort auditoria;
    private volatile Estado estado = new Estado(false, 0, 0, List.of(), null, null);

    public ExternalIdController(GestionSmartOltPort smartOlt, AuditTrailPort auditoria) {
        this.smartOlt = smartOlt;
        this.auditoria = auditoria;
    }

    @GetMapping
    public Estado estado() {
        return estado;
    }

    @PostMapping
    public synchronized Estado poner(@RequestBody Pedido cuerpo, @AuthenticationPrincipal UsuarioAutenticado usuario, HttpServletRequest request) {
        if (estado.enCurso()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya se están poniendo External ID: espera a que termine.");
        }
        List<String> sns = cuerpo == null || cuerpo.sns() == null ? List.of() : cuerpo.sns().stream().distinct().toList();
        if (sns.isEmpty() || sns.size() > MAXIMO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escoge entre 1 y " + MAXIMO + " ONUs.");
        }
        String ip = request.getRemoteAddr();
        estado = new Estado(true, sns.size(), 0, List.of(), Instant.now(), null);
        Thread.ofVirtual().name("external-id").start(() -> correr(sns, usuario.id(), ip));
        return estado;
    }

    private void correr(List<String> sns, long usuarioId, String ip) {
        int hechas = 0;
        List<Error> errores = new ArrayList<>();
        for (String sn : sns) {
            String motivo = null;
            String id = null;
            try {
                id = smartOlt.ponerExternalIdIgualAlSn(sn);
                hechas++;
            } catch (RuntimeException e) {
                motivo = String.valueOf(e.getMessage());
                errores.add(new Error(sn, motivo));
            }
            auditoria.record(AuditEntry.builder(ActorType.USUARIO, AuditAction.EXTERNAL_ID_PUESTO).usuarioId(usuarioId).ipOrigen(ip)
                    .sn(sn).sistema("SmartOLT").peticion(Map.of("comando", "SmartOLT: update_unique_external_id_by_sn"))
                    .resultado(motivo == null ? "OK" : "FALLO").mensaje(motivo == null ? "External ID: " + id : motivo).build());
            estado = new Estado(true, sns.size(), hechas, List.copyOf(errores), estado.iniciadoEn(), null);
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        estado = new Estado(false, sns.size(), hechas, List.copyOf(errores), estado.iniciadoEn(), Instant.now());
    }
}
