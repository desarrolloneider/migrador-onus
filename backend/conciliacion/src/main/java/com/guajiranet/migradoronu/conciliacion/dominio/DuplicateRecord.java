package com.guajiranet.migradoronu.conciliacion.dominio;

import java.util.Objects;

/** Un registro repetido de SmartOLT que no se usó. Se guarda como dato y nunca se toca (RN-35). */
public record DuplicateRecord(OnuPosition position, boolean enLinea) {

    public DuplicateRecord {
        Objects.requireNonNull(position, "position");
    }
}
