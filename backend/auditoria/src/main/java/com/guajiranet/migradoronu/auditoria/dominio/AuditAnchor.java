package com.guajiranet.migradoronu.auditoria.dominio;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Un ancla fuera de la base: el id y la huella del último evento en un momento dado (arquitectura 10.2). Detecta lo
 * que la cadena sola no puede: que se borren los últimos eventos o que se reescriba la cadena completa.
 *
 * @param reason por qué se ancló, por ejemplo {@code DIARIA} o {@code LOTE_TERMINADO}
 */
public record AuditAnchor(Instant anchoredAt, String reason, long eventId, String hash) {

    public static final String DAILY = "DIARIA";
    public static final String BATCH_FINISHED = "LOTE_TERMINADO";

    private static final Pattern REASON = Pattern.compile("[A-Z_]{1,40}");

    public AuditAnchor {
        Objects.requireNonNull(anchoredAt, "anchoredAt");
        if (reason == null || !REASON.matcher(reason).matches()) {
            throw new IllegalArgumentException("Motivo de ancla inválido: " + reason);
        }
        if (eventId <= 0) {
            throw new IllegalArgumentException("El id del evento anclado debe ser positivo: " + eventId);
        }
        Hashes.requireValid(hash, false, "hash");
    }
}
