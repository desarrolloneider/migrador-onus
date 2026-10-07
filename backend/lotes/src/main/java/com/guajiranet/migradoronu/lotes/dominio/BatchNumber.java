package com.guajiranet.migradoronu.lotes.dominio;

import java.util.regex.Pattern;

/** Número de lote, por ejemplo {@code LOT-2026-000045}. Único y nunca se reutiliza (spec 8.1). */
public record BatchNumber(String value) {

    private static final Pattern FORMAT = Pattern.compile("LOT-\\d{4}-\\d{6}");

    public BatchNumber {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Número de lote inválido: " + value + " (formato LOT-2026-000045)");
        }
    }

    public static BatchNumber of(int year, long sequence) {
        if (year < 2000 || year > 9999) {
            throw new IllegalArgumentException("Año inválido para un número de lote: " + year);
        }
        if (sequence < 1 || sequence > 999_999) {
            throw new IllegalArgumentException("Consecutivo de lote fuera de rango: " + sequence);
        }
        return new BatchNumber("LOT-%04d-%06d".formatted(year, sequence));
    }

    @Override
    public String toString() {
        return value;
    }
}
