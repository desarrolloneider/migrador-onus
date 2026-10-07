package com.guajiranet.migradoronu.auditoria.infraestructura.entrada.programacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import com.guajiranet.migradoronu.auditoria.dominio.AuditAnchor;
import com.guajiranet.migradoronu.auditoria.puertos.entrada.AnchorAuditPort;

/**
 * Ancla diaria (arquitectura 10.2). Corre solo en el proceso ejecutor, que es único, para que haya un solo escritor del
 * archivo de anclas. Se activa con {@code migrador.auditoria.anclas.programadas=true}.
 */
public class DailyAuditAnchorJob {

    private static final Logger log = LoggerFactory.getLogger(DailyAuditAnchorJob.class);

    private final AnchorAuditPort anchors;

    public DailyAuditAnchorJob(AnchorAuditPort anchors) {
        this.anchors = anchors;
    }

    @Scheduled(cron = "${migrador.auditoria.anclas.cron:0 0 5 * * *}", zone = "UTC")
    public void anchorDaily() {
        try {
            anchors.anchorNow(AuditAnchor.DAILY)
                    .ifPresentOrElse(
                            anchor -> log.info("Ancla diaria de la auditoría escrita en el evento {}", anchor.eventId()),
                            () -> log.info("Ancla diaria omitida: la auditoría todavía no tiene eventos"));
        } catch (RuntimeException e) {
            log.error("No se pudo escribir el ancla diaria de la auditoría", e);
        }
    }
}
