package com.guajiranet.migradoronu.ejecucion.puertos.salida;

import com.guajiranet.migradoronu.procedimientos.dominio.OnuTarget;
import com.guajiranet.migradoronu.procedimientos.dominio.RevalidationResult;

/** Revalidación de una ONU en SmartOLT y Wispro (P01). Lo implementa ensamblaje llamando a conciliacion. */
public interface OnuRevalidationPort {

    RevalidationResult revalidate(OnuTarget target);
}
