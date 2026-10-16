package com.guajiranet.migradoronu.lotes.aplicacion;

import java.time.Instant;
import java.util.Map;

import com.guajiranet.migradoronu.lotes.dominio.BatchFilters;
import com.guajiranet.migradoronu.lotes.dominio.BatchMode;
import com.guajiranet.migradoronu.lotes.dominio.BatchStatus;

/**
 * Un lote completo para las pantallas (ESP-04, ESP-08). Los usuarios van por id: el nombre lo pone quien muestra.
 *
 * @param contadores entran, excluidas, yaTienenPerfil, pendientes, enCurso, completadas, fallidas, excluidasAlEjecutar,
 *                   noIniciadas
 */
public record LoteDetalle(long id, String numero, BatchMode modo, boolean simulacion, BatchStatus estado, BatchFilters filtros,
                          long creadoPor, Instant creadoEn, Instant venceEn, Long confirmadoPor, Instant confirmadoEn,
                          Instant iniciadoEn, Instant terminadoEn, String motivoDetencion, String procedimientoVersion,
                          Map<String, Long> contadores, boolean archivado) {

    public LoteDetalle(long id, String numero, BatchMode modo, boolean simulacion, BatchStatus estado, BatchFilters filtros,
                       long creadoPor, Instant creadoEn, Instant venceEn, Long confirmadoPor, Instant confirmadoEn,
                       Instant iniciadoEn, Instant terminadoEn, String motivoDetencion, String procedimientoVersion,
                       Map<String, Long> contadores) {
        this(id, numero, modo, simulacion, estado, filtros, creadoPor, creadoEn, venceEn, confirmadoPor, confirmadoEn, iniciadoEn,
                terminadoEn, motivoDetencion, procedimientoVersion, contadores, false);
    }

    public BatchView resumen() {
        return new BatchView(id, numero, modo, simulacion, estado, filtros.marca(), creadoEn);
    }
}
