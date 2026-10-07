package com.guajiranet.migradoronu.conciliacion.dominio;

import java.util.Objects;

/**
 * Un registro de ONU tal como lo entrega SmartOLT (RN-02). Un mismo SN puede venir en varios registros (RN-35).
 *
 * @param rawSn       el SN como lo escribe SmartOLT; se lleva al formato común al clasificar (PC-20)
 * @param ip          la IP de la ONU, llave del cruce con Wispro (RN-01); {@code null} si SmartOLT no la trae (PC-32)
 * @param perfilTr069 el perfil actual si SmartOLT lo muestra (PC-09); {@code null} si no
 */
public record OnuCandidate(String rawSn, String marca, String modelo, OnuPosition position, boolean enLinea,
                           String ip, String perfilTr069) {

    public OnuCandidate {
        if (rawSn == null || rawSn.isBlank()) {
            throw new IllegalArgumentException("SmartOLT entregó una ONU sin SN");
        }
        Objects.requireNonNull(position, "position");
    }
}
