package com.guajiranet.migradoronu.comun.puertos;

import java.time.Instant;

/** Un evento de auditoría de una ejecución, tal como lo necesita la línea de tiempo de la ONU (ESP-09). */
public record EventoDeEjecucion(Instant hora, String accion, String paso, String resultado, String codigo, String mensaje,
                                String comando, String respuesta) {
}
