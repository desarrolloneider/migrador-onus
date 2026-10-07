package com.guajiranet.migradoronu.procedimientos.dominio;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tarjeta de la OLT en formato marco/ranura, por ejemplo {@code 0/1} (se entra con {@code interface gpon 0/1}). */
public record BoardRef(int frame, int slot) {

    private static final Pattern FORMAT = Pattern.compile("(\\d{1,2})/(\\d{1,2})");

    public BoardRef {
        if (frame < 0 || slot < 0) {
            throw new IllegalArgumentException("Tarjeta inválida: " + frame + "/" + slot);
        }
    }

    public static BoardRef parse(String text) {
        Matcher matcher = text == null ? null : FORMAT.matcher(text.strip());
        if (matcher == null || !matcher.matches()) {
            throw new IllegalArgumentException("Tarjeta inválida: '" + text + "' (formato esperado 0/n)");
        }
        return new BoardRef(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
    }

    @Override
    public String toString() {
        return frame + "/" + slot;
    }
}
