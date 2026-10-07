package com.guajiranet.migradoronu.ensamblaje.ejecucion;

import org.springframework.stereotype.Component;

import com.guajiranet.migradoronu.ejecucion.dominio.FailureCode;
import com.guajiranet.migradoronu.ejecucion.dominio.OnuRunStatus;
import com.guajiranet.migradoronu.ejecucion.puertos.salida.BatchProgressPort;
import com.guajiranet.migradoronu.lotes.aplicacion.OnuFinishedNotice;
import com.guajiranet.migradoronu.lotes.dominio.OnuOutcome;
import com.guajiranet.migradoronu.lotes.puertos.entrada.CloseBatchPort;

/** C1: ejecucion avisa a lotes que una ONU terminó. */
@Component
public class BatchProgressModuleAdapter implements BatchProgressPort {

    private final CloseBatchPort batches;

    public BatchProgressModuleAdapter(CloseBatchPort batches) {
        this.batches = batches;
    }

    @Override
    public void onuFinished(long loteId, String sn, OnuRunStatus finalStatus, FailureCode failureCode) {
        batches.onuFinished(new OnuFinishedNotice(loteId, sn, toOutcome(finalStatus),
                failureCode == null ? null : failureCode.name()));
    }

    static OnuOutcome toOutcome(OnuRunStatus status) {
        return switch (status) {
            case COMPLETADA -> OnuOutcome.COMPLETADA;
            case FALLIDA -> OnuOutcome.FALLIDA;
            case EXCLUIDA_AL_EJECUTAR -> OnuOutcome.EXCLUIDA_AL_EJECUTAR;
            case NO_INICIADA -> OnuOutcome.NO_INICIADA;
            case PENDIENTE, REVALIDANDO, EN_CURSO ->
                    throw new IllegalArgumentException("La ONU no ha terminado: está " + status);
        };
    }
}
