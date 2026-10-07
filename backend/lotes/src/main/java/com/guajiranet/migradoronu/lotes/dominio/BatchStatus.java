package com.guajiranet.migradoronu.lotes.dominio;

/** Estados del lote (spec 7 y 7.1). Terminado, terminado con fallas, detenido y descartado son definitivos. */
public enum BatchStatus {
    VISTA_PREVIA,
    CONFIRMADO,
    EN_EJECUCION,
    DETENIENDOSE,
    TERMINADO,
    TERMINADO_CON_FALLAS,
    DETENIDO,
    DESCARTADO;

    public boolean isFinal() {
        return this == TERMINADO || this == TERMINADO_CON_FALLAS || this == DETENIDO || this == DESCARTADO;
    }

    /** Mientras un lote está así, la configuración no se puede cambiar (RN-28). */
    public boolean isRunning() {
        return this == EN_EJECUCION || this == DETENIENDOSE;
    }
}
