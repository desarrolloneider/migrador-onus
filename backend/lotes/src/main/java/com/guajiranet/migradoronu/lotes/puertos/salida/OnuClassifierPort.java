package com.guajiranet.migradoronu.lotes.puertos.salida;

import java.util.List;
import java.util.Optional;

import com.guajiranet.migradoronu.lotes.dominio.BatchFilters;
import com.guajiranet.migradoronu.lotes.dominio.ClassifiedOnu;

/** Clasificación de ONUs al armar. Lo implementa ensamblaje llamando a conciliacion. */
public interface OnuClassifierPort {

    List<ClassifiedOnu> classify(BatchFilters filters);

    /** Vacío si el SN no está en SmartOLT (ESP-10). */
    Optional<ClassifiedOnu> classifySingle(String sn);
}
