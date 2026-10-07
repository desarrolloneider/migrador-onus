package com.guajiranet.migradoronu.procedimientos.dominio;

/** Puerto PON e ID de la ONU dentro de su tarjeta: los datos que van en los comandos (por ejemplo, 0 y 33). */
public record OnuPosition(int pon, int onuId) {

    public OnuPosition {
        if (pon < 0 || onuId < 0) {
            throw new IllegalArgumentException("Puerto PON e ID deben ser mayores o iguales a cero: " + pon + " " + onuId);
        }
    }
}
