package com.guajiranet.migradoronu.comun.puertos;

/** No se pudo confirmar un evento en la auditoría. Quien la recibe no manda ningún comando (RN-30). */
public class AuditUnavailableException extends RuntimeException {

    public AuditUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
