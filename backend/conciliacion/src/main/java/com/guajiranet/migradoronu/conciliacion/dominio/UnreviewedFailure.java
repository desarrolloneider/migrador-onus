package com.guajiranet.migradoronu.conciliacion.dominio;

/** Una fallida sin revisar que impide que la ONU entre a un lote (RN-26, EXC_FALLIDA_SIN_REVISAR). */
public record UnreviewedFailure(String loteNumero, String paso) {
}
