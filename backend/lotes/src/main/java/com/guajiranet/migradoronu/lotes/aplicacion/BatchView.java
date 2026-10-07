package com.guajiranet.migradoronu.lotes.aplicacion;

import java.time.Instant;

import com.guajiranet.migradoronu.lotes.dominio.BatchMode;
import com.guajiranet.migradoronu.lotes.dominio.BatchStatus;

/** Resumen de un lote para las pantallas y para los demás módulos. Los contadores se agregan con ESP-08. */
public record BatchView(long id, String numero, BatchMode modo, boolean simulacion, BatchStatus estado, String marca,
                        Instant creadoEn) {
}
