package com.guajiranet.migradoronu.lotes.aplicacion;

final class IdempotencyKeys {

    private IdempotencyKeys() {
    }

    static void require(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Falta la clave de idempotencia (RN-24)");
        }
    }
}
