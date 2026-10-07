package com.guajiranet.migradoronu.lotes.dominio;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Los parámetros con que se arma y se confirma un lote. Se congelan al confirmar (RN-37); si la huella cambió entre
 * la vista previa y la confirmación, no se confirma (RN-36).
 */
public record ParametersSnapshot(Map<String, Object> values, String fingerprint) {

    public ParametersSnapshot {
        if (fingerprint == null || fingerprint.isBlank()) {
            throw new IllegalArgumentException("La huella de los parámetros es obligatoria (RN-36)");
        }
        values = values == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
