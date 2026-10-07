package com.guajiranet.migradoronu.lotes.aplicacion;

/**
 * Confirmar una vista previa (ESP-05) o confirmarla como simulación (ESP-11). Se ejecuta una sola vez aunque llegue
 * repetida (RN-24).
 */
public record ConfirmBatchCommand(long loteId, long usuarioId, boolean simulacion, String idempotencyKey) {

    public ConfirmBatchCommand {
        IdempotencyKeys.require(idempotencyKey);
    }
}
