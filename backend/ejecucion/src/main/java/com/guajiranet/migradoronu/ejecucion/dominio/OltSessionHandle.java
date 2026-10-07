package com.guajiranet.migradoronu.ejecucion.dominio;

import java.util.Objects;

/** Una sesión abierta en la consola de una OLT. Se abre y se cierra por cada ONU (RN-34). */
public record OltSessionHandle(String sessionId, OltRef olt) {

    public OltSessionHandle {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("El identificador de la sesión es obligatorio");
        }
        Objects.requireNonNull(olt, "olt");
    }
}
