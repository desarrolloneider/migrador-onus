package com.guajiranet.migradoronu.procedimientos.dominio;

/**
 * Resultado de P01. Si algo cambió, dice qué dato, cómo estaba y cómo está (EXC_CAMBIO_AL_EJECUTAR).
 */
public record RevalidationResult(boolean unchanged, String changedField, String before, String after) {

    public RevalidationResult {
        if (!unchanged && (changedField == null || changedField.isBlank())) {
            throw new IllegalArgumentException("Si algo cambió hay que decir qué dato cambió (RN-20)");
        }
    }

    public static RevalidationResult same() {
        return new RevalidationResult(true, null, null, null);
    }

    public static RevalidationResult changed(String field, String before, String after) {
        return new RevalidationResult(false, field, before, after);
    }
}
