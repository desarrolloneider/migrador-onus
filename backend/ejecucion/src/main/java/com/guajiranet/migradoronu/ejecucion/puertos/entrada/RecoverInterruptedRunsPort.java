package com.guajiranet.migradoronu.ejecucion.puertos.entrada;

/** Al arrancar el ejecutor, antes de tomar trabajo (ESP-16). */
public interface RecoverInterruptedRunsPort {

    /** Deja como fallidas con FALLO_INTERRUMPIDA las ONUs que quedaron "revalidando" o "en curso". Devuelve cuántas. */
    int recoverInterruptedRuns();
}
