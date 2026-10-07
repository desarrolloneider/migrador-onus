package com.guajiranet.migradoronu.lotes.aplicacion;

/** Descartar una vista previa. No se borra nada: queda en la auditoría (respuesta del 3 de octubre). */
public record DiscardBatchCommand(long loteId, long usuarioId, String idempotencyKey) {

    public DiscardBatchCommand {
        IdempotencyKeys.require(idempotencyKey);
    }
}
