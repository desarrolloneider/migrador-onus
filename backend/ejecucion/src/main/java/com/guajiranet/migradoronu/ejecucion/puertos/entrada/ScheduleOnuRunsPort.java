package com.guajiranet.migradoronu.ejecucion.puertos.entrada;

import com.guajiranet.migradoronu.ejecucion.aplicacion.ScheduleOnuRunsCommand;

/**
 * Crea las ejecuciones de un lote recién confirmado, en la misma transacción de la confirmación (arquitectura 7).
 * La arquitectura v3.0 nombra RunSchedulerPort en lotes pero no el puerto de entrada que lo atiende; es este.
 */
public interface ScheduleOnuRunsPort {

    void schedule(ScheduleOnuRunsCommand command);

    /** Las fallas anteriores de estas ONUs quedan resueltas: la OLT mostró que ya tienen el perfil nuevo. */
    default void resolverFallidas(long loteId, long usuarioId, java.util.List<String> sns) {
    }
}
