package com.guajiranet.migradoronu.procedimientos.dominio;

import java.util.Objects;

/**
 * La ONU sobre la que trabaja un paso, con los datos congelados en la vista previa. La tarjeta, el puerto y el ID
 * nunca se suponen: salen de SmartOLT y se confirman en P01 (RN-14).
 */
public record OnuTarget(String sn, String ip, String contrato, String oltCodigo, BoardRef board, OnuPosition position) {

    public OnuTarget {
        if (sn == null || sn.isBlank()) {
            throw new IllegalArgumentException("El SN es obligatorio");
        }
        if (oltCodigo == null || oltCodigo.isBlank()) {
            throw new IllegalArgumentException("La OLT es obligatoria");
        }
        Objects.requireNonNull(board, "board");
        Objects.requireNonNull(position, "position");
    }
}
