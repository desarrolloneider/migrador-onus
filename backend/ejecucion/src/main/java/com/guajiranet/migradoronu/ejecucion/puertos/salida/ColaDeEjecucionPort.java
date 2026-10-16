package com.guajiranet.migradoronu.ejecucion.puertos.salida;

import java.util.Optional;

import com.guajiranet.migradoronu.ejecucion.aplicacion.TrabajoOnu;

/**
 * La cola de trabajo es la tabla ejecuciones_onu (arquitectura 9.1). Tomar la siguiente usa FOR UPDATE SKIP LOCKED y
 * la pasa a EN_CURSO en la misma transacción.
 */
public interface ColaDeEjecucionPort {

    /** Antes de tomar: las pendientes de un lote que se está deteniendo pasan a NO_INICIADA y el lote se cierra. */
    void atenderDetenciones();

    /** La siguiente ONU pendiente de esa OLT. Cada OLT la atiende un solo hilo (RN-34). */
    Optional<TrabajoOnu> tomarSiguiente(long procesoId, String oltCodigo);

    /** Las OLT que tienen ONUs pendientes en lotes que se pueden ejecutar, la más antigua primero. */
    java.util.List<String> oltsConPendientes();

    /**
     * Cierra la ejecución, recalcula los contadores del lote y, si ya no le quedan ONUs, lo cierra.
     *
     * @param estado COMPLETADA, FALLIDA o EXCLUIDA_AL_EJECUTAR
     */
    void terminar(long ejecucionId, String estado, String paso, String codigoFallo, String mensaje);

    /**
     * Al arrancar el único ejecutor (RN-40): las ONUs que quedaron en curso por una caída pasan a FALLIDA con
     * FALLO_INTERRUMPIDA, para que alguien las revise en la OLT. Devuelve cuántas.
     */
    int recuperarInterrumpidas();

    /**
     * Las fallidas del lote (todas, o solo esas ejecuciones) vuelven a PENDIENTE en el mismo lote, y el lote vuelve a
     * ejecución. No toca las que ya están activas en otro lote. Devuelve los SN que quedaron pendientes.
     */
    java.util.List<String> reintentarFallidas(long loteId, java.util.List<Long> ejecuciones);

    /** El paso en que va una ejecución en curso, para la pantalla en vivo. */
    void avanzarPaso(long ejecucionId, String paso);

    long registrarProceso(String host, long pid);

    void latido(long procesoId);

    void detenerProceso(long procesoId);
}
