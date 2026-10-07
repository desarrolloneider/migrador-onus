package com.guajiranet.migradoronu.lotes.puertos.entrada;

import com.guajiranet.migradoronu.lotes.aplicacion.OnuFinishedNotice;

/** Lo llama ejecucion (por ensamblaje) cada vez que termina una ONU: contadores y cierre del lote (C1 a C4). */
public interface CloseBatchPort {

    void onuFinished(OnuFinishedNotice notice);
}
