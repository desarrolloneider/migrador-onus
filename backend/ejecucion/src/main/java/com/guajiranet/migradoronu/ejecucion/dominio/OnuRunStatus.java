package com.guajiranet.migradoronu.ejecucion.dominio;

/** Estados de una ONU dentro de un lote (spec 7 y 7.2). */
public enum OnuRunStatus {
    PENDIENTE,
    REVALIDANDO,
    EN_CURSO,
    COMPLETADA,
    FALLIDA,
    EXCLUIDA_AL_EJECUTAR,
    NO_INICIADA;

    /** Una ONU en un estado activo ocupa su lugar: no puede entrar a otro lote (RN-16). */
    public boolean isActive() {
        return this == PENDIENTE || this == REVALIDANDO || this == EN_CURSO;
    }

    public boolean isFinal() {
        return !isActive();
    }
}
