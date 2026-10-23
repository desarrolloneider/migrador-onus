package com.guajiranet.migradoronu.web;

import java.time.Duration;
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
import com.guajiranet.migradoronu.ejecucion.aplicacion.EstadoGestionOnu;
import com.guajiranet.migradoronu.ejecucion.puertos.salida.GestionSmartOltPort;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Solo administradores (ReglasDePermiso: /api/v1/vlan-gestion): quita la VLAN 46 en SmartOLT a las ONUs escogidas, sin
 * lote ni ejecutor, como el arreglo del 21 de octubre en OLT_SAN_JUAN. De a una, con pausa (límites de SmartOLT), en un
 * hilo aparte: la pantalla consulta el avance. Solo SmartOLT: no toca la OLT (SmartOLT le quita el perfil TR-069; se le
 * devuelve con un lote) ni reinicia. Cada ONU queda en la auditoría.
 */
@RestController
@RequestMapping("/api/v1/vlan-gestion/quitar-directo")
public class QuitarVlanDirectoController {

    private static final Duration PAUSA = Duration.ofMillis(1500);
    private static final int MAXIMO = 500;

    public record QuitarRequest(List<String> sns) {
    }

    public record Error(String sn, String motivo) {
    }

    public record Estado(boolean enCurso, int total, int hechas, int yaSinVlan, List<Error> errores, Instant iniciadoEn, Instant terminadoEn) {
    }

    private final GestionSmartOltPort smartOlt;
    private final AuditTrailPort auditoria;
    private volatile Estado estado = new Estado(false, 0, 0, 0, List.of(), null, null);

    public QuitarVlanDirectoController(GestionSmartOltPort smartOlt, AuditTrailPort auditoria) {
        this.smartOlt = smartOlt;
        this.auditoria = auditoria;
    }

    @GetMapping
    public Estado estado() {
        return estado;
    }

    @PostMapping
    public synchronized Estado quitar(@RequestBody QuitarRequest cuerpo, @AuthenticationPrincipal UsuarioAutenticado usuario,
                                      HttpServletRequest request) {
        if (estado.enCurso()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya hay una quitada de VLAN 46 en curso: espera a que termine.");
        }
        List<String> sns = cuerpo == null || cuerpo.sns() == null ? List.of() : cuerpo.sns().stream().distinct().toList();
        if (sns.isEmpty() || sns.size() > MAXIMO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escoge entre 1 y " + MAXIMO + " ONUs.");
        }
        String ip = request.getRemoteAddr();
        estado = new Estado(true, sns.size(), 0, 0, List.of(), Instant.now(), null);
        Thread.ofVirtual().name("quitar-vlan46").start(() -> correr(sns, usuario.id(), ip));
        return estado;
    }

    private void correr(List<String> sns, long usuarioId, String ip) {
        int hechas = 0;
        int yaSin = 0;
        List<Error> errores = new ArrayList<>();
        for (String sn : sns) {
            String motivo = null;
            try {
                EstadoGestionOnu g = smartOlt.leer(sn).orElse(null);
                if (g == null) {
                    motivo = "SmartOLT no tiene esa ONU.";
                } else if (g.modo() == null || g.modo().equalsIgnoreCase("Inactive")) {
                    yaSin++;
                } else {
                    // Sin External ID, SmartOLT no acepta el cambio: se le pone su SN y se sigue.
                    String id = g.idExterno() == null || g.idExterno().isBlank() ? smartOlt.ponerExternalIdIgualAlSn(sn) : g.idExterno();
                    smartOlt.desactivarGestion(id);
                    hechas++;
                }
            } catch (RuntimeException e) {
                motivo = "SmartOLT respondió: " + e.getMessage();
            }
            if (motivo != null) {
                errores.add(new Error(sn, motivo));
            }
            auditoria.record(AuditEntry.builder(ActorType.USUARIO, AuditAction.VLAN_GESTION_QUITADA).usuarioId(usuarioId).ipOrigen(ip)
                    .sn(sn).sistema("SmartOLT").peticion(Map.of("comando", "SmartOLT: IP de gestión Inactiva (sin lote)"))
                    .resultado(motivo == null ? "OK" : "FALLO").mensaje(motivo == null ? "VLAN 46 quitada en SmartOLT (o ya no la tenía)." : motivo)
                    .build());
            estado = new Estado(true, sns.size(), hechas, yaSin, List.copyOf(errores), estado.iniciadoEn(), null);
            try {
                Thread.sleep(PAUSA.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        estado = new Estado(false, sns.size(), hechas, yaSin, List.copyOf(errores), estado.iniciadoEn(), Instant.now());
    }
}
