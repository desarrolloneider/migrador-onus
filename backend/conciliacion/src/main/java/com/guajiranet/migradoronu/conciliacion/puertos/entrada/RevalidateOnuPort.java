package com.guajiranet.migradoronu.conciliacion.puertos.entrada;

import com.guajiranet.migradoronu.conciliacion.aplicacion.RevalidateOnuCommand;
import com.guajiranet.migradoronu.conciliacion.aplicacion.RevalidationOutcome;

/** Revalidación de una sola ONU justo antes de ejecutarla (P01, RN-17). */
public interface RevalidateOnuPort {

    RevalidationOutcome revalidate(RevalidateOnuCommand command);
}
