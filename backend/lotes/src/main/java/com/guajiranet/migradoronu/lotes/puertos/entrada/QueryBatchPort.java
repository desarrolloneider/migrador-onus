package com.guajiranet.migradoronu.lotes.puertos.entrada;

import java.util.List;
import java.util.Optional;

import com.guajiranet.migradoronu.lotes.aplicacion.BatchView;

/** Consultas de lotes (ESP-04, ESP-08). */
public interface QueryBatchPort {

    Optional<BatchView> findById(long loteId);

    /** El lote en ejecución o deteniéndose, si hay uno (RN-28). */
    Optional<BatchView> findRunning();

    List<BatchView> findRecent(int limit);

    Optional<BatchView> findByNumero(String numero);
}
