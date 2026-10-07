package com.guajiranet.migradoronu.conciliacion.aplicacion;

/** Si algo cambió desde la vista previa: qué dato, cómo estaba y cómo está (EXC_CAMBIO_AL_EJECUTAR). */
public record RevalidationOutcome(boolean unchanged, String changedField, String before, String after) {

    public static RevalidationOutcome same() {
        return new RevalidationOutcome(true, null, null, null);
    }

    public static RevalidationOutcome changed(String field, String before, String after) {
        return new RevalidationOutcome(false, field, before, after);
    }
}
