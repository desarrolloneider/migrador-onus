package com.guajiranet.migradoronu.auditoria.aplicacion;

import java.util.List;

import com.guajiranet.migradoronu.auditoria.dominio.StoredAuditEvent;

public record AuditPage(List<StoredAuditEvent> events, long total, int page, int size) {

    public AuditPage {
        events = List.copyOf(events);
    }
}
