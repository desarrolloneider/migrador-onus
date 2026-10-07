package com.guajiranet.migradoronu.ejecucion.dominio;

/** La OLT o un sistema rechazó las credenciales de la herramienta: el motor detiene el lote entero (RN-31). */
public class CredentialsRejectedException extends StepFailure {

    public CredentialsRejectedException(String system, String rawResponse) {
        super(FailureCode.FALLO_CREDENCIALES, system + " rechazó las credenciales de la herramienta", rawResponse);
    }
}
