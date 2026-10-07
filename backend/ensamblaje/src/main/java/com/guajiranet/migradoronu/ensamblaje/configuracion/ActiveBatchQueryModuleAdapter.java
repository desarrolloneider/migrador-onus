package com.guajiranet.migradoronu.ensamblaje.configuracion;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.guajiranet.migradoronu.configuracion.puertos.salida.ActiveBatchQueryPort;
import com.guajiranet.migradoronu.lotes.aplicacion.BatchView;
import com.guajiranet.migradoronu.lotes.puertos.entrada.QueryBatchPort;

/** RN-28: configuracion pregunta a lotes si hay un lote en ejecución antes de aceptar un cambio. */
@Component
public class ActiveBatchQueryModuleAdapter implements ActiveBatchQueryPort {

    private final QueryBatchPort batches;

    public ActiveBatchQueryModuleAdapter(QueryBatchPort batches) {
        this.batches = batches;
    }

    @Override
    public Optional<String> runningBatchNumber() {
        return batches.findRunning().map(BatchView::numero);
    }
}
