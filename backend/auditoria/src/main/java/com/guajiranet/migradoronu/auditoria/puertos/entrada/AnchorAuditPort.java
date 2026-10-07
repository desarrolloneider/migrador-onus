package com.guajiranet.migradoronu.auditoria.puertos.entrada;

import java.util.Optional;

import com.guajiranet.migradoronu.auditoria.dominio.AuditAnchor;

/** Escribe un ancla con el último evento: una vez al día y al terminar cada lote (arquitectura 10.2). */
public interface AnchorAuditPort {

    /** El ancla escrita, o vacío si la auditoría todavía no tiene eventos. */
    Optional<AuditAnchor> anchorNow(String reason);
}
