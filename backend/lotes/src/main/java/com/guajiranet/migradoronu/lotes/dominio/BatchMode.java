package com.guajiranet.migradoronu.lotes.dominio;

/**
 * Lote o prueba individual (DEC-03). La simulación no es un modo aparte sino una marca del lote que se fija al
 * confirmar, porque se puede simular tanto un lote como una sola ONU (ESP-11).
 */
public enum BatchMode {
    LOTE,
    INDIVIDUAL
}
