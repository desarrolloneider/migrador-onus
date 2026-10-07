package com.guajiranet.migradoronu.lotes.aplicacion;

import java.util.Objects;

import com.guajiranet.migradoronu.lotes.dominio.BatchFilters;

/** Armar la vista previa de un lote (ESP-03). */
public record BuildBatchCommand(long usuarioId, BatchFilters filtros) {

    public BuildBatchCommand {
        Objects.requireNonNull(filtros, "filtros");
    }
}
