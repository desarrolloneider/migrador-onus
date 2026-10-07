package com.guajiranet.migradoronu.ensamblaje.conciliacion;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.guajiranet.migradoronu.conciliacion.puertos.salida.OltSettingsQueryPort;
import com.guajiranet.migradoronu.configuracion.dominio.OltSettings;
import com.guajiranet.migradoronu.configuracion.puertos.entrada.QueryParametersPort;

/** K8: tarjetas permitidas y acceso por consola de una OLT, leídos de configuracion. */
@Component
public class OltSettingsModuleAdapter implements OltSettingsQueryPort {

    private final QueryParametersPort parameters;

    public OltSettingsModuleAdapter(QueryParametersPort parameters) {
        this.parameters = parameters;
    }

    @Override
    public Set<String> allowedBoards(String oltCodigo) {
        return parameters.oltSettings(oltCodigo)
                .map(settings -> settings.tarjetas().values())
                .orElse(Set.of());
    }

    @Override
    public boolean consoleAccessConfigured(String oltCodigo) {
        return parameters.oltSettings(oltCodigo)
                .map(OltSettings::consoleAccessConfigured)
                .orElse(false);
    }
}
