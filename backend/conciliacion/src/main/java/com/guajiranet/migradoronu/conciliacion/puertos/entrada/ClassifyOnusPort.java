package com.guajiranet.migradoronu.conciliacion.puertos.entrada;

import java.util.List;
import java.util.Optional;

import com.guajiranet.migradoronu.conciliacion.aplicacion.ClassifyOnusCommand;
import com.guajiranet.migradoronu.conciliacion.dominio.Classification;

/** Clasificación de ONUs para armar una vista previa (ESP-03, ESP-04, ESP-10). */
public interface ClassifyOnusPort {

    /**
     * Una clasificación por SN. Si una descarga queda incompleta lanza {@code IncompleteDownloadException} y no
     * devuelve nada parcial (RN-39).
     */
    List<Classification> classify(ClassifyOnusCommand command);

    /** Clasificación de una sola ONU por su SN; vacía si el SN no está en SmartOLT (ESP-10). */
    Optional<Classification> classifySingle(String sn);
}
