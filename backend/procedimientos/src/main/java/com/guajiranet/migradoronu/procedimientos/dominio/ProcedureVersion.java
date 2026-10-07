package com.guajiranet.migradoronu.procedimientos.dominio;

/** Versión de un procedimiento. Un lote se ejecuta completo con la versión con que se confirmó (RN-37). */
public record ProcedureVersion(String value) {

    public ProcedureVersion {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("La versión del procedimiento es obligatoria");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
