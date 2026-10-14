package com.guajiranet.migradoronu.conciliacion.puertos.salida;

import java.time.Instant;
import java.util.Optional;

/** Cuándo se reportó la ONU por última vez al ACS (GenieACS). Vacío si el ACS no la conoce. */
public interface UltimoReporteAcsPort {

    Optional<Instant> ultimoReporte(String sn);

    /** Los SN (hexadecimal) que en el ACS ya tienen el servicio WAN listo (TR069_INTERNET). Una sola consulta. */
    default java.util.Set<String> conServicioWanListo() {
        return java.util.Set.of();
    }
}
