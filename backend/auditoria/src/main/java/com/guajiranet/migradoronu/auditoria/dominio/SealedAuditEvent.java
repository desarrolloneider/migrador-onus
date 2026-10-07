package com.guajiranet.migradoronu.auditoria.dominio;

import java.util.Objects;

/** Un evento con su huella y la del evento anterior, listo para guardarse. */
public record SealedAuditEvent(AuditEvent event, String previousHash, String hash) {

    public SealedAuditEvent {
        Objects.requireNonNull(event, "event");
        Hashes.requireValid(previousHash, true, "previousHash");
        Hashes.requireValid(hash, false, "hash");
    }
}
