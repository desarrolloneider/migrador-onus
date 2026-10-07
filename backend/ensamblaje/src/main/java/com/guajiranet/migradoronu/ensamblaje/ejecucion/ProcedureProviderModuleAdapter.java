package com.guajiranet.migradoronu.ensamblaje.ejecucion;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.guajiranet.migradoronu.ejecucion.puertos.salida.ProcedureProviderPort;
import com.guajiranet.migradoronu.procedimientos.dominio.Procedure;
import com.guajiranet.migradoronu.procedimientos.dominio.ProcedureVersion;
import com.guajiranet.migradoronu.procedimientos.puertos.entrada.GetProcedurePort;

/** ejecucion pide los pasos de la versión con que se confirmó el lote (RN-37). */
@Component
public class ProcedureProviderModuleAdapter implements ProcedureProviderPort {

    private final GetProcedurePort procedures;

    public ProcedureProviderModuleAdapter(GetProcedurePort procedures) {
        this.procedures = procedures;
    }

    @Override
    public Optional<Procedure> byVersion(String brand, ProcedureVersion version) {
        return procedures.byVersion(brand, version);
    }
}
