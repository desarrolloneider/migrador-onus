package com.guajiranet.migradoronu.auditoria.aplicacion;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

import com.guajiranet.migradoronu.auditoria.dominio.AuditAnchor;
import com.guajiranet.migradoronu.auditoria.puertos.entrada.AnchorAuditPort;
import com.guajiranet.migradoronu.auditoria.puertos.salida.AuditAnchorPort;
import com.guajiranet.migradoronu.auditoria.puertos.salida.AuditRepositoryPort;

public class AnchorAuditUseCase implements AnchorAuditPort {

    private final AuditRepositoryPort repository;
    private final AuditAnchorPort anchors;
    private final Clock clock;

    public AnchorAuditUseCase(AuditRepositoryPort repository, AuditAnchorPort anchors, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.anchors = Objects.requireNonNull(anchors, "anchors");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Optional<AuditAnchor> anchorNow(String reason) {
        return repository.head().map(head -> {
            AuditAnchor anchor = new AuditAnchor(clock.instant().truncatedTo(ChronoUnit.MILLIS), reason, head.id(), head.hash());
            anchors.append(anchor);
            return anchor;
        });
    }
}
