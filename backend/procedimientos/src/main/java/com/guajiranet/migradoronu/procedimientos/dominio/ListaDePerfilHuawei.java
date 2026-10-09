package com.guajiranet.migradoronu.procedimientos.dominio;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * La respuesta de {@code display ont tr069-server-profile bound-info profile-id <n>} en una OLT Huawei: qué ONUs
 * tienen ese perfil, por puerto. Confirmado con OLT-CONEJO el 7 de octubre de 2026:
 * <pre>
 *   F/S/P       ONT List
 *   0/ 0/7      2,12-13
 * </pre>
 * Si una línea no se entiende, no se adivina: {@link #leer} devuelve vacío (RN-13).
 *
 * @param ontsPorPuerto llave {@code "0/0/7"} (sin espacios), valor los ONT ID
 */
public record ListaDePerfilHuawei(Map<String, SortedSet<Integer>> ontsPorPuerto) {

    private static final Pattern FILA = Pattern.compile("^\\s*(\\d+)\\s*/\\s*(\\d+)\\s*/\\s*(\\d+)\\s+([\\d,\\-\\s]+?)\\s*$");
    private static final Pattern RANGO = Pattern.compile("(\\d+)(?:-(\\d+))?");
    private static final Pattern IGNORABLE = Pattern.compile("^\\s*(-+|F/S/P\\s+ONT List.*|)\\s*$");

    public ListaDePerfilHuawei {
        Map<String, SortedSet<Integer>> copia = new LinkedHashMap<>();
        ontsPorPuerto.forEach((k, v) -> copia.put(k, Collections.unmodifiableSortedSet(new TreeSet<>(v))));
        ontsPorPuerto = Collections.unmodifiableMap(copia);
    }

    /** Vacío si alguna línea no tiene el formato conocido o si la OLT respondió un error. */
    public static Optional<ListaDePerfilHuawei> leer(String texto) {
        if (texto != null && texto.toLowerCase(java.util.Locale.ROOT).contains("the profile is not bound")) {
            return Optional.of(new ListaDePerfilHuawei(Map.of()));
        }
        if (texto == null || RespuestaHuawei.esError(texto)) {
            return Optional.empty();
        }
        texto = RespuestaHuawei.sinAvisos(texto);
        Map<String, SortedSet<Integer>> resultado = new LinkedHashMap<>();
        for (String linea : texto.split("\\R")) {
            if (IGNORABLE.matcher(linea).matches()) {
                continue;
            }
            Matcher fila = FILA.matcher(linea);
            if (!fila.matches()) {
                return Optional.empty();
            }
            String puerto = fila.group(1) + "/" + fila.group(2) + "/" + fila.group(3);
            SortedSet<Integer> onts = resultado.computeIfAbsent(puerto, k -> new TreeSet<>());
            for (String parte : fila.group(4).replace(" ", "").split(",")) {
                Matcher r = RANGO.matcher(parte);
                if (!r.matches()) {
                    return Optional.empty();
                }
                int desde = Integer.parseInt(r.group(1));
                int hasta = r.group(2) == null ? desde : Integer.parseInt(r.group(2));
                if (hasta < desde || hasta - desde > 256) {
                    return Optional.empty();
                }
                for (int id = desde; id <= hasta; id++) {
                    onts.add(id);
                }
            }
        }
        return Optional.of(new ListaDePerfilHuawei(resultado));
    }

    /** ¿Está la ONU en esta lista? {@code tarjeta} como "0/0", {@code pon} y {@code ontId} como en SmartOLT. */
    public boolean contiene(String tarjeta, int pon, int ontId) {
        SortedSet<Integer> onts = ontsPorPuerto.get(tarjeta.replace(" ", "") + "/" + pon);
        return onts != null && onts.contains(ontId);
    }

    public int total() {
        return ontsPorPuerto.values().stream().mapToInt(SortedSet::size).sum();
    }
}
