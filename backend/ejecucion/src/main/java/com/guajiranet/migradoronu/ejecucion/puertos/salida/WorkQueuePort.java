package com.guajiranet.migradoronu.ejecucion.puertos.salida;

import java.util.Optional;

/**
 * La cola de trabajo es la tabla ejecuciones_onu (arquitectura 9.1). El adaptador toma la siguiente ONU pendiente con
 * FOR UPDATE SKIP LOCKED, en orden de confirmación del lote y orden de la ONU, y la pasa a "revalidando".
 */
public interface WorkQueuePort {

    /** El id de la ejecución tomada, o vacío si no hay ninguna pendiente. */
    Optional<Long> claimNext();
}
