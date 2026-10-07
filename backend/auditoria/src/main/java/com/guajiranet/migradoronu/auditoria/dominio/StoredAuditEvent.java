package com.guajiranet.migradoronu.auditoria.dominio;

/**
 * Un evento leído de la base. Si la fila no se puede interpretar (por ejemplo, alguien escribió una acción que no
 * existe), {@code event} es {@code null} y {@code unreadableReason} dice por qué: la verificación lo informa como
 * alteración en lugar de detenerse con un error.
 */
public record StoredAuditEvent(long id, String previousHash, String hash, AuditEvent event, String unreadableReason) {

    public static StoredAuditEvent readable(long id, String previousHash, String hash, AuditEvent event) {
        return new StoredAuditEvent(id, previousHash, hash, event, null);
    }

    public static StoredAuditEvent unreadable(long id, String previousHash, String hash, String reason) {
        return new StoredAuditEvent(id, previousHash, hash, null, reason);
    }

    public boolean isReadable() {
        return event != null;
    }
}
