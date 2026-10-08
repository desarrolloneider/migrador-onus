package com.guajiranet.migradoronu.lotes.puertos.salida;

import java.util.List;

import com.guajiranet.migradoronu.lotes.dominio.ScheduledOnu;

/**
 * Crea las ejecuciones del lote en ejecucion, dentro de la misma transacción de la confirmación (arquitectura 7).
 * Lo implementa ensamblaje.
 */
public interface RunSchedulerPort {

    void schedule(long loteId, String procedimientoVersion, List<ScheduledOnu> onus);

    /**
     * La OLT mostró que estas ONUs ya tienen el perfil nuevo (vista previa {@code loteId}): sus fallas anteriores quedan
     * resueltas y no se ofrecen para reintentar.
     */
    default void yaTienenElPerfil(long loteId, long usuarioId, List<String> sns) {
    }
}
