package com.guajiranet.migradoronu.auditoria.puertos.entrada;

import com.guajiranet.migradoronu.comun.puertos.AuditEntry;

/**
 * Guarda un evento con su huella encadenada (arquitectura 10.2). Devuelve solo cuando quedó confirmado; si no puede,
 * lanza {@code AuditUnavailableException} (RN-30).
 */
public interface RecordAuditEventPort {

    void record(AuditEntry entry);
}
