package com.guajiranet.migradoronu.conciliacion.puertos.salida;

import java.util.List;

import com.guajiranet.migradoronu.conciliacion.dominio.OnuCandidate;

/** Lectura de ONUs en SmartOLT (arquitectura 9.6, PC-09, PC-32). Solo lectura (RN-03). */
public interface OnuInventoryPort {

    /** Los registros (OLT|tarjeta|pon|onu, en mayúsculas la OLT) sin External ID en SmartOLT, según la última copia. */
    default java.util.Set<String> sinExternalId() {
        return java.util.Set.of();
    }

    /**
     * Todas las ONUs de la marca con los filtros dados, con descarga paginada comprobada completa; si no lo está,
     * lanza {@code IncompleteDownloadException} (RN-39). Los filtros en {@code null} no filtran.
     */
    List<OnuCandidate> listOnus(String marca, String oltCodigo, String tarjeta, Integer pon);

    /** Todos los registros de un SN; puede haber más de uno (RN-35). */
    List<OnuCandidate> findBySn(String sn);
}
