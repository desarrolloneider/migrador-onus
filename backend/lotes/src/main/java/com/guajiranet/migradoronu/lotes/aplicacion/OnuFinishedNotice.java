package com.guajiranet.migradoronu.lotes.aplicacion;

import java.util.Objects;

import com.guajiranet.migradoronu.lotes.dominio.OnuOutcome;

/** Aviso de ejecucion: una ONU del lote terminó (C1 del recorrido). */
public record OnuFinishedNotice(long loteId, String sn, OnuOutcome resultado, String codigoFallo) {

    public OnuFinishedNotice {
        Objects.requireNonNull(resultado, "resultado");
        if (resultado == OnuOutcome.FALLIDA && (codigoFallo == null || codigoFallo.isBlank())) {
            throw new IllegalArgumentException("Una ONU fallida trae su código de fallo: " + sn);
        }
    }
}
