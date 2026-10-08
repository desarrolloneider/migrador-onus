package com.guajiranet.migradoronu.comun.puertos;

import java.util.List;

/**
 * Puerto transversal: los eventos de auditoría de una ejecución, en orden. La auditoría es la fuente de verdad de qué
 * se mandó y qué respondió la OLT; ejecucion los lee aquí en vez de guardarlos dos veces. Lo implementa ensamblaje.
 */
public interface RunTimelinePort {

    List<EventoDeEjecucion> deEjecucion(long ejecucionId, long loteId, String sn);
}
