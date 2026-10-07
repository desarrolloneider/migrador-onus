package com.guajiranet.migradoronu.auditoria.puertos.salida;

import java.util.List;

import com.guajiranet.migradoronu.auditoria.dominio.AuditAnchor;

/** Archivo de anclas de solo agregar, fuera de la base (arquitectura 10.2). */
public interface AuditAnchorPort {

    void append(AuditAnchor anchor);

    /** Todas las anclas. Si el archivo no se puede leer, lanza {@code AnchorsUnreadableException}. */
    List<AuditAnchor> readAll();
}
