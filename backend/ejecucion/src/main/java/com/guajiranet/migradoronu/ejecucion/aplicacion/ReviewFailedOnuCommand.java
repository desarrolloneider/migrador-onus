package com.guajiranet.migradoronu.ejecucion.aplicacion;

/** Revisión de una fallida (ESP-14). La nota es obligatoria, de mínimo 10 caracteres (spec 8.1). */
public record ReviewFailedOnuCommand(long ejecucionId, long usuarioId, String nota, String idempotencyKey) {

    public static final int MIN_NOTE_LENGTH = 10;

    public ReviewFailedOnuCommand {
        if (nota == null || nota.strip().length() < MIN_NOTE_LENGTH) {
            throw new IllegalArgumentException("Escribe qué revisaste o qué hiciste (mínimo 10 caracteres).");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Falta la clave de idempotencia (RN-24)");
        }
        nota = nota.strip();
    }
}
