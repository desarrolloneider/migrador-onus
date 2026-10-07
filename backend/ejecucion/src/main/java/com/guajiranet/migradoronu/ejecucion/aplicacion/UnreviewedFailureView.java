package com.guajiranet.migradoronu.ejecucion.aplicacion;

import com.guajiranet.migradoronu.ejecucion.dominio.FailureCode;
import com.guajiranet.migradoronu.procedimientos.dominio.StepCode;

public record UnreviewedFailureView(long ejecucionId, long loteId, StepCode paso, FailureCode codigo) {
}
