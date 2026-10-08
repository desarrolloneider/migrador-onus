package com.guajiranet.migradoronu.ejecucion.aplicacion;

import java.time.Instant;

/** Una ejecución de ONU con los datos de su lote y de la vista previa, para las pantallas (ESP-08, ESP-09, ESP-14). */
public record EjecucionVista(long id, long loteId, String loteNumero, boolean simulacion, String sn, String cliente, String ip,
                             String contrato, String modelo, String olt, String tarjeta, int pon, int onuId, int orden, String estado,
                             String pasoActual, String codigo, String mensaje, Instant iniciadaEn, Instant terminadaEn,
                             boolean revisada, Long revisadaPor, Instant revisadaEn, String notaRevision,
                             Long resueltaEnLoteId, String resueltaEnLote) {

    public Long duracionMs() {
        return iniciadaEn == null || terminadaEn == null ? null : terminadaEn.toEpochMilli() - iniciadaEn.toEpochMilli();
    }
}
