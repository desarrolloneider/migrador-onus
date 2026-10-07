package com.guajiranet.migradoronu.auditoria.aplicacion;

import java.util.Objects;

import com.guajiranet.migradoronu.auditoria.puertos.entrada.QueryAuditPort;
import com.guajiranet.migradoronu.auditoria.puertos.salida.AuditRepositoryPort;

public class QueryAuditUseCase implements QueryAuditPort {

    private final AuditRepositoryPort repository;

    public QueryAuditUseCase(AuditRepositoryPort repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public AuditPage search(AuditSearchCriteria criteria) {
        return repository.search(Objects.requireNonNull(criteria, "criteria"));
    }
}
