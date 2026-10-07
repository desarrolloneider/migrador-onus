package com.guajiranet.migradoronu.lotes.dominio;

/** Un registro repetido de SmartOLT que no se usó, tal como se muestra en la vista previa (RN-35, ESP-04). */
public record DuplicateEntry(String oltCodigo, String tarjeta, int pon, int onuId, boolean enLinea) {
}
