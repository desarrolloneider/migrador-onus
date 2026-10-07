package com.guajiranet.migradoronu.ejecucion.puertos.entrada;

import com.guajiranet.migradoronu.ejecucion.aplicacion.ReviewFailedOnuCommand;

/** Marcar una fallida como revisada, con nota (ESP-14, RN-26). */
public interface ReviewFailedOnuPort {

    void review(ReviewFailedOnuCommand command);
}
