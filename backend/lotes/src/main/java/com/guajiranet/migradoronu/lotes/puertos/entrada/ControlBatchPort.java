package com.guajiranet.migradoronu.lotes.puertos.entrada;

import com.guajiranet.migradoronu.lotes.aplicacion.BatchView;
import com.guajiranet.migradoronu.lotes.aplicacion.ConfirmBatchCommand;
import com.guajiranet.migradoronu.lotes.aplicacion.DiscardBatchCommand;
import com.guajiranet.migradoronu.lotes.aplicacion.StopBatchCommand;

/** Confirmar, simular, descartar y detener (ESP-05, ESP-11, ESP-12). Solo las transiciones de la spec 7.1. */
public interface ControlBatchPort {

    BatchView confirm(ConfirmBatchCommand command);

    BatchView discard(DiscardBatchCommand command);

    BatchView stop(StopBatchCommand command);

    /**
     * Archiva los lotes que ya no se mueven: dejan de salir en la lista, sin borrar nada. Con {@code usuarioId} null lo hace
     * la consola. Devuelve cuántos se archivaron.
     */
    default int archivarTerminados(Long usuarioId, String ipOrigen) {
        throw new UnsupportedOperationException("Archivar no está disponible");
    }
}
