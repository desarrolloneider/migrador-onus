package com.guajiranet.migradoronu.lotes.dominio;

/** Cómo terminó una ONU, visto desde lotes: lo que necesita para sus contadores (spec 7.1). */
public enum OnuOutcome {
    COMPLETADA,
    FALLIDA,
    EXCLUIDA_AL_EJECUTAR,
    NO_INICIADA
}
