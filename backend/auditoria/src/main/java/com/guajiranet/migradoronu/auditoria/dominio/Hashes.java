package com.guajiranet.migradoronu.auditoria.dominio;

import java.util.regex.Pattern;

final class Hashes {

    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");

    private Hashes() {
    }

    static void requireValid(String hash, boolean nullable, String field) {
        if (hash == null ? !nullable : !SHA256_HEX.matcher(hash).matches()) {
            throw new IllegalArgumentException(field + " no es una huella SHA-256 válida: " + hash);
        }
    }
}
