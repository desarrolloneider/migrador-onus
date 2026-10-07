package com.guajiranet.migradoronu.auditoria.aplicacion;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.guajiranet.migradoronu.auditoria.dominio.AnchorsUnreadableException;
import com.guajiranet.migradoronu.auditoria.dominio.AuditAnchor;
import com.guajiranet.migradoronu.auditoria.dominio.ChainHead;
import com.guajiranet.migradoronu.auditoria.dominio.ChainVerifier;
import com.guajiranet.migradoronu.auditoria.dominio.IntegrityReport;
import com.guajiranet.migradoronu.auditoria.dominio.StoredAuditEvent;
import com.guajiranet.migradoronu.auditoria.puertos.entrada.RecordAuditEventPort;
import com.guajiranet.migradoronu.auditoria.puertos.entrada.VerifyAuditIntegrityPort;
import com.guajiranet.migradoronu.auditoria.puertos.salida.AuditAnchorPort;
import com.guajiranet.migradoronu.auditoria.puertos.salida.AuditRepositoryPort;
import com.guajiranet.migradoronu.comun.puertos.AuditAction;
import com.guajiranet.migradoronu.comun.puertos.AuditEntry;

/**
 * Recorre toda la cadena por páginas y la compara con las anclas (ESP-13). El resultado queda en la auditoría como
 * INTEGRIDAD_VERIFICADA o INTEGRIDAD_ROTA.
 */
public class VerifyAuditIntegrityUseCase implements VerifyAuditIntegrityPort {

    static final int PAGE_SIZE = 1_000;

    private final AuditRepositoryPort repository;
    private final AuditAnchorPort anchors;
    private final RecordAuditEventPort auditLog;

    public VerifyAuditIntegrityUseCase(AuditRepositoryPort repository, AuditAnchorPort anchors, RecordAuditEventPort auditLog) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.anchors = Objects.requireNonNull(anchors, "anchors");
        this.auditLog = Objects.requireNonNull(auditLog, "auditLog");
    }

    @Override
    public IntegrityReport verify(VerifyAuditIntegrityCommand command) {
        IntegrityReport report = check();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("eventosRevisados", report.checkedEvents());
        details.put("primerEventoRoto", report.brokenEventId());
        details.put("ultimoEvento", report.lastEventId());
        auditLog.record(AuditEntry.builder(command.actor(),
                        report.intact() ? AuditAction.INTEGRIDAD_VERIFICADA : AuditAction.INTEGRIDAD_ROTA)
                .usuarioId(command.usuarioId())
                .ipOrigen(command.ipOrigen())
                .sistema("auditoria")
                .respuesta(details)
                .resultado(report.intact() ? "INTACTA" : "ROTA")
                .mensaje(report.message())
                .build());
        return report;
    }

    private IntegrityReport check() {
        // Primero las anclas y después el último evento: toda ancla legítima apunta a un evento que ya existía.
        List<AuditAnchor> anchorList;
        try {
            anchorList = anchors.readAll();
        } catch (AnchorsUnreadableException e) {
            return IntegrityReport.broken(0, null, "El archivo de anclas no se puede leer: " + e.getMessage());
        }
        long upToId = repository.head().map(ChainHead::id).orElse(0L);
        ChainVerifier verifier = new ChainVerifier(anchorList);
        long afterId = 0;
        while (afterId < upToId) {
            List<StoredAuditEvent> page = repository.readChain(afterId, upToId, PAGE_SIZE);
            if (page.isEmpty()) {
                break;
            }
            for (StoredAuditEvent row : page) {
                if (!verifier.accept(row)) {
                    return verifier.finish();
                }
                afterId = row.id();
            }
        }
        return verifier.finish();
    }
}
