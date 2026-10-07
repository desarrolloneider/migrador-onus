package com.guajiranet.migradoronu.lotes.puertos.salida;

import com.guajiranet.migradoronu.lotes.dominio.ParametersSnapshot;

/** Los parámetros vigentes con su huella (RN-36, RN-37). Lo implementa ensamblaje llamando a configuracion. */
public interface ParametersSnapshotPort {

    ParametersSnapshot current();
}
