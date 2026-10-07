package com.guajiranet.migradoronu.ensamblaje.transversal;

import org.springframework.stereotype.Component;

import com.guajiranet.migradoronu.auditoria.puertos.entrada.RecordAuditEventPort;
import com.guajiranet.migradoronu.comun.puertos.AuditEntry;
import com.guajiranet.migradoronu.comun.puertos.AuditTrailPort;

/** El único adaptador de auditoría: todos los módulos registran por AuditTrailPort y aquí se entrega a auditoria. */
@Component
public class AuditTrailModuleAdapter implements AuditTrailPort {

    private final RecordAuditEventPort auditLog;

    public AuditTrailModuleAdapter(RecordAuditEventPort auditLog) {
        this.auditLog = auditLog;
    }

    @Override
    public void record(AuditEntry entry) {
        auditLog.record(entry);
    }
}
