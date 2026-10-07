package com.guajiranet.migradoronu.comun.puertos;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aviso para las pantallas en vivo (ESP-08).
 *
 * @param canal a quién le interesa, por ejemplo {@code lote:45} u {@code onu-run:812}
 * @param tipo  qué cambió, por ejemplo {@code PASO_TERMINADO}
 * @param datos lo mínimo para repintar la fila; la pantalla pide el resto si lo necesita
 */
public record LiveEvent(String canal, String tipo, Map<String, Object> datos) {

    public LiveEvent {
        if (canal == null || canal.isBlank()) {
            throw new IllegalArgumentException("El canal es obligatorio");
        }
        if (tipo == null || tipo.isBlank()) {
            throw new IllegalArgumentException("El tipo es obligatorio");
        }
        datos = datos == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(datos));
    }
}
