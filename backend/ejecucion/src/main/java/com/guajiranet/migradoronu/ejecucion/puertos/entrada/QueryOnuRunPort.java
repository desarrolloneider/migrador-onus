package com.guajiranet.migradoronu.ejecucion.puertos.entrada;

import java.util.Optional;

import com.guajiranet.migradoronu.ejecucion.aplicacion.ActiveRunView;
import com.guajiranet.migradoronu.ejecucion.aplicacion.UnreviewedFailureView;

/** Consultas sobre las ejecuciones de ONU (ESP-09, ESP-14). El detalle y la lista de fallidas se agregan con sus historias. */
public interface QueryOnuRunPort {

    /** La ejecución activa de un SN, si está en un lote activo (RN-16). */
    Optional<ActiveRunView> findActiveRun(String sn);

    /** La última fallida sin revisar de un SN (RN-26). */
    Optional<UnreviewedFailureView> findUnreviewedFailure(String sn);
}
