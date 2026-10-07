package com.guajiranet.migradoronu.ejecucion.aplicacion;

import com.guajiranet.migradoronu.ejecucion.dominio.OnuRunStatus;

public record ActiveRunView(long ejecucionId, long loteId, OnuRunStatus estado) {
}
