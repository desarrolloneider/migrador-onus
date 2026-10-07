package com.guajiranet.migradoronu.comun.puertos;

/** Quién origina un evento de auditoría (arquitectura 10.1). */
public enum ActorType {
    USUARIO,
    /** El proceso ejecutor. Sus eventos llevan el usuario que confirmó el lote (spec 4.1). */
    PROCESO_EJECUTOR,
    /** Comandos por consola en el servidor: usuarios y verificación de la auditoría (ESP-02). */
    CONSOLA
}
