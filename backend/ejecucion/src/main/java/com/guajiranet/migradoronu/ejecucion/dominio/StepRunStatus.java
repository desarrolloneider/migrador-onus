package com.guajiranet.migradoronu.ejecucion.dominio;

/** Estados de un paso (spec 7.3). */
public enum StepRunStatus {
    PENDIENTE,
    EN_CURSO,
    CORRECTO,
    YA_ESTABA,
    FALLIDO,
    NO_EJECUTADO,
    SIMULADO
}
