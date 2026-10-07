package com.guajiranet.migradoronu.ejecucion.puertos.salida;

import com.guajiranet.migradoronu.ejecucion.dominio.FailureCode;
import com.guajiranet.migradoronu.ejecucion.dominio.OnuRunStatus;

/** Avisa a lotes que una ONU terminó, para que actualice contadores y cierre el lote si no quedan pendientes. */
public interface BatchProgressPort {

    /**
     * @param finalStatus  un estado final de la ONU
     * @param failureCode  el motivo si la ONU falló; {@code null} en otro caso
     */
    void onuFinished(long loteId, String sn, OnuRunStatus finalStatus, FailureCode failureCode);
}
