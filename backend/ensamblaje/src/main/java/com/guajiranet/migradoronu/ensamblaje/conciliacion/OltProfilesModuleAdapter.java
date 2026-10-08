package com.guajiranet.migradoronu.ensamblaje.conciliacion;

import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.guajiranet.migradoronu.conciliacion.puertos.salida.OltProfilesQueryPort;
import com.guajiranet.migradoronu.conciliacion.puertos.salida.OltSettingsQueryPort;
import com.guajiranet.migradoronu.ejecucion.puertos.entrada.ReadOltProfilesPort;

/** conciliacion pregunta a ejecucion (que sabe hablar con la consola) qué perfil tiene cada ONU en la OLT. */
@Component
public class OltProfilesModuleAdapter implements OltProfilesQueryPort {

    private static final Logger LOG = LoggerFactory.getLogger(OltProfilesModuleAdapter.class);

    private final ReadOltProfilesPort lector;
    private final OltSettingsQueryPort olts;

    public OltProfilesModuleAdapter(ReadOltProfilesPort lector, OltSettingsQueryPort olts) {
        this.lector = lector;
        this.olts = olts;
    }

    @Override
    public Optional<Map<String, String>> perfiles(String oltCodigo) {
        if (!olts.consoleAccessConfigured(oltCodigo)) {
            return Optional.empty();
        }
        try {
            return Optional.of(lector.perfilesPorOnu(oltCodigo));
        } catch (RuntimeException e) {
            LOG.warn("No se pudieron leer los perfiles de {}: {}", oltCodigo, e.getMessage());
            return Optional.empty();
        }
    }
}
