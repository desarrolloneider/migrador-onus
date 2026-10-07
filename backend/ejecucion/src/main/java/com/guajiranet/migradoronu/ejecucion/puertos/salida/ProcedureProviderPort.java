package com.guajiranet.migradoronu.ejecucion.puertos.salida;

import java.util.Optional;

import com.guajiranet.migradoronu.procedimientos.dominio.Procedure;
import com.guajiranet.migradoronu.procedimientos.dominio.ProcedureVersion;

/** Pasos y versión del procedimiento de una marca. Lo implementa ensamblaje llamando a procedimientos. */
public interface ProcedureProviderPort {

    Optional<Procedure> byVersion(String brand, ProcedureVersion version);
}
