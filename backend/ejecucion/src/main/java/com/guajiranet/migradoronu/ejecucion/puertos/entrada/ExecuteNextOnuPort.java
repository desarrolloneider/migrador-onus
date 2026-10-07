package com.guajiranet.migradoronu.ejecucion.puertos.entrada;

/** Lo llama el ciclo del proceso ejecutor (ESP-05, ESP-06, ESP-07). */
public interface ExecuteNextOnuPort {

    /** Toma y ejecuta la siguiente ONU pendiente. Devuelve {@code false} si no había ninguna. */
    boolean executeNext();
}
