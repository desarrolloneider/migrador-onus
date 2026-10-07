package com.guajiranet.migradoronu.auditoria.aplicacion;

import java.time.Instant;

import com.guajiranet.migradoronu.comun.puertos.AuditAction;

/**
 * Filtros de la auditoría (ESP-13). Los que van en {@code null} no filtran. El resultado sale en orden de ocurrencia,
 * para que buscar un SN muestre todos sus eventos de todos los lotes, en orden.
 */
public record AuditSearchCriteria(Instant desde, Instant hasta, Long usuarioId, Long loteId, String sn, String sistema,
                                  String paso, String resultado, AuditAction accion, int page, int size) {

    public static final int MAX_PAGE_SIZE = 500;

    public AuditSearchCriteria {
        if (page < 0) {
            throw new IllegalArgumentException("La página empieza en 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("El tamaño de página debe estar entre 1 y " + MAX_PAGE_SIZE);
        }
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha inicial es posterior a la final");
        }
    }

    public static AuditSearchCriteria bySn(String sn, int page, int size) {
        return new AuditSearchCriteria(null, null, null, null, sn, null, null, null, null, page, size);
    }
}
