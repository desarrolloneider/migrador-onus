package com.guajiranet.migradoronu.configuracion.dominio;

import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import com.guajiranet.migradoronu.comun.errores.ErrorDeNegocio;

/**
 * Tarjetas de una OLT que la herramienta puede tocar (RN-14, DEC-29). Empieza vacía: una OLT sin tarjetas no entra a
 * ningún lote (PC-39).
 */
public record AllowedBoards(Set<String> values) {

    private static final Pattern FORMAT = Pattern.compile("\\d{1,2}/\\d{1,2}");

    public AllowedBoards {
        TreeSet<String> ordenadas = new TreeSet<>();
        for (String board : values == null ? Set.<String>of() : values) {
            String limpia = board == null ? "" : board.strip();
            if (!FORMAT.matcher(limpia).matches()) {
                throw ErrorDeNegocio.datosInvalidos("Tarjeta inválida: \"" + board + "\". El formato es 0/n.");
            }
            ordenadas.add(limpia);
        }
        values = java.util.Collections.unmodifiableSortedSet(ordenadas);
    }

    public static AllowedBoards none() {
        return new AllowedBoards(Set.of());
    }

    public boolean allows(String board) {
        return values.contains(board);
    }
}
