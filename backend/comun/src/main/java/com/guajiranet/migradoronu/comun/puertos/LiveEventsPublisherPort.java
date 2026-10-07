package com.guajiranet.migradoronu.comun.puertos;

/**
 * Puerto transversal de tiempo real. El adaptador publica con NOTIFY dentro de la transacción en curso, así la
 * pantalla solo ve cambios ya confirmados; el proceso web lo reparte por WebSocket (arquitectura 7 y 8).
 * Si la pantalla no está conectada no pasa nada: el lote nunca se detiene por eso (ESP-08).
 */
public interface LiveEventsPublisherPort {

    void publish(LiveEvent event);
}
