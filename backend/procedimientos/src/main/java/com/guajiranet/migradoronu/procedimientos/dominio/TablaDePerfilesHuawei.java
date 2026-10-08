package com.guajiranet.migradoronu.procedimientos.dominio;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * La respuesta de {@code display ont tr069-server-profile all} (confirmada con OLT-CONEJO el 7 de octubre de 2026):
 * <pre>
 *  Profile ID     Profile Name                     Binding Times
 *  2              GUAJIRANET                       6
 *  3              GUAJIRANET1                      10
 * </pre>
 * El ID de un perfil puede cambiar de una OLT a otra; el nombre es lo que se configura (PC-02).
 */
public record TablaDePerfilesHuawei(Map<String, Integer> idPorNombre) {

    private static final Pattern FILA = Pattern.compile("^\\s*(\\d+)\\s+(\\S+)\\s+(\\d+)\\s*$");

    public TablaDePerfilesHuawei {
        idPorNombre = Collections.unmodifiableMap(new LinkedHashMap<>(idPorNombre));
    }

    /** Vacío si la OLT respondió un error o no hay ninguna fila reconocible. */
    public static Optional<TablaDePerfilesHuawei> leer(String texto) {
        if (texto == null || RespuestaHuawei.esError(texto)) {
            return Optional.empty();
        }
        texto = RespuestaHuawei.sinAvisos(texto);
        Map<String, Integer> perfiles = new LinkedHashMap<>();
        for (String linea : texto.split("\\R")) {
            Matcher m = FILA.matcher(linea);
            if (m.matches()) {
                perfiles.put(m.group(2).toUpperCase(Locale.ROOT), Integer.valueOf(m.group(1)));
            }
        }
        return perfiles.isEmpty() ? Optional.empty() : Optional.of(new TablaDePerfilesHuawei(perfiles));
    }

    /** El ID del perfil con ese nombre exacto, sin importar mayúsculas. */
    public Optional<Integer> idDe(String nombre) {
        return nombre == null ? Optional.empty() : Optional.ofNullable(idPorNombre.get(nombre.strip().toUpperCase(Locale.ROOT)));
    }
}
