package com.guajiranet.migradoronu.auditoria.dominio;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Cadena de huellas (arquitectura 10.2): huella = SHA-256(huella anterior + JSON canónico del evento). Alterar,
 * quitar o reordenar un evento cambia su huella o rompe el enlace con el siguiente.
 */
public final class HashChain {

    private HashChain() {
    }

    public static SealedAuditEvent seal(String previousHash, AuditEvent event) {
        return new SealedAuditEvent(event, previousHash, hashOf(previousHash, event));
    }

    public static String hashOf(String previousHash, AuditEvent event) {
        String material = (previousHash == null ? "" : previousHash) + "\n" + CanonicalJson.write(event.canonicalForm());
        return HexFormat.of().formatHex(sha256().digest(material.getBytes(StandardCharsets.UTF_8)));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("La JVM no tiene SHA-256", e);
        }
    }
}
