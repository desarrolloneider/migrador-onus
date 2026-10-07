package com.guajiranet.migradoronu.procedimientos.puertos.entrada;

import java.util.Optional;

import com.guajiranet.migradoronu.procedimientos.dominio.Procedure;
import com.guajiranet.migradoronu.procedimientos.dominio.ProcedureVersion;

public interface GetProcedurePort {

    /** Procedimiento vigente de una marca; vacío si la marca no tiene procedimiento (RN-15, EXC_MARCA_SIN_PROCEDIMIENTO). */
    Optional<Procedure> current(String brand);

    /** La versión exacta con que se confirmó un lote (RN-37). */
    Optional<Procedure> byVersion(String brand, ProcedureVersion version);
}
