package com.guajiranet.migradoronu.auditoria.puertos.entrada;

import com.guajiranet.migradoronu.auditoria.aplicacion.VerifyAuditIntegrityCommand;
import com.guajiranet.migradoronu.auditoria.dominio.IntegrityReport;

/** "Verificar integridad" (ESP-13): dice si algún evento se alteró o se borró, y lo deja en la auditoría. */
public interface VerifyAuditIntegrityPort {

    IntegrityReport verify(VerifyAuditIntegrityCommand command);
}
