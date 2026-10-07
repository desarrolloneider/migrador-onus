package com.guajiranet.migradoronu.ensamblaje.lotes;

import org.springframework.stereotype.Component;

import com.guajiranet.migradoronu.configuracion.aplicacion.ParametersView;
import com.guajiranet.migradoronu.configuracion.puertos.entrada.QueryParametersPort;
import com.guajiranet.migradoronu.lotes.dominio.ParametersSnapshot;
import com.guajiranet.migradoronu.lotes.puertos.salida.ParametersSnapshotPort;

/** Los parámetros vigentes para congelarlos en el lote. La huella es la revisión de configuracion (RN-36). */
@Component
public class ParametersSnapshotModuleAdapter implements ParametersSnapshotPort {

    private final QueryParametersPort parameters;

    public ParametersSnapshotModuleAdapter(QueryParametersPort parameters) {
        this.parameters = parameters;
    }

    @Override
    public ParametersSnapshot current() {
        ParametersView view = parameters.currentParameters();
        return new ParametersSnapshot(view.valores(), "rev-" + view.revision());
    }
}
