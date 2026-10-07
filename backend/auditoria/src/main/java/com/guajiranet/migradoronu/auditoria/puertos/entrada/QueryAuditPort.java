package com.guajiranet.migradoronu.auditoria.puertos.entrada;

import com.guajiranet.migradoronu.auditoria.aplicacion.AuditPage;
import com.guajiranet.migradoronu.auditoria.aplicacion.AuditSearchCriteria;

/** Búsqueda en la auditoría por fecha, usuario, lote, SN, sistema, paso, acción y resultado (ESP-13). */
public interface QueryAuditPort {

    AuditPage search(AuditSearchCriteria criteria);
}
