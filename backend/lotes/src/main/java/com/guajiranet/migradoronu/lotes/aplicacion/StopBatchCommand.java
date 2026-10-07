package com.guajiranet.migradoronu.lotes.aplicacion;

/** Detener un lote: la ONU en curso termina o falla y no empieza ninguna más (ESP-12, RN-23). */
public record StopBatchCommand(long loteId, long usuarioId, String idempotencyKey) {

    public StopBatchCommand {
        IdempotencyKeys.require(idempotencyKey);
    }
}
