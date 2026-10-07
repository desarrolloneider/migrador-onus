package com.guajiranet.migradoronu.conciliacion.aplicacion;

import java.util.Objects;

import com.guajiranet.migradoronu.conciliacion.dominio.OnuPosition;

/** Lo que dijo la vista previa de una ONU, para compararlo con lo que dicen hoy SmartOLT y Wispro (P01, RN-17). */
public record RevalidateOnuCommand(String sn, String ip, String contrato, OnuPosition position) {

    public RevalidateOnuCommand {
        if (sn == null || sn.isBlank()) {
            throw new IllegalArgumentException("El SN es obligatorio");
        }
        Objects.requireNonNull(position, "position");
    }
}
