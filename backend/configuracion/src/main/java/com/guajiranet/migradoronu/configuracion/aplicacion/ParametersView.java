package com.guajiranet.migradoronu.configuracion.aplicacion;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Los parámetros vigentes (spec 7.5). Un valor {@code null} significa "sin valor", como guardar_configuracion_olt
 * mientras no se decida PC-36.
 *
 * @param revision cambia cada vez que cambia cualquier parámetro; sirve para detectar RN-36
 */
public record ParametersView(Map<String, Object> valores, long revision) {

    public ParametersView {
        valores = valores == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(valores));
    }
}
