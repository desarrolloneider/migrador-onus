package com.guajiranet.migradoronu.conciliacion.dominio;

/**
 * Dónde está una ONU según SmartOLT. Es el modelo propio de conciliacion; ejecucion tiene el suyo y ensamblaje
 * traduce entre los dos (capa anticorrupción, arquitectura 13).
 */
public record OnuPosition(String oltCodigo, String tarjeta, int pon, int onuId) {

    public OnuPosition {
        if (oltCodigo == null || oltCodigo.isBlank()) {
            throw new IllegalArgumentException("La OLT es obligatoria");
        }
        if (tarjeta == null || tarjeta.isBlank()) {
            throw new IllegalArgumentException("La tarjeta es obligatoria");
        }
        if (pon < 0 || onuId < 0) {
            throw new IllegalArgumentException("Puerto PON e ID deben ser mayores o iguales a cero");
        }
    }
}
