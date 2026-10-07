package com.guajiranet.migradoronu.lotes.dominio;

/** Una ONU del grupo "entran" que pasa a esperar turno al confirmar el lote. */
public record ScheduledOnu(long loteItemId, String sn, int orden) {
}
