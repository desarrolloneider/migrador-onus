package com.guajiranet.migradoronu.auditoria.dominio;

/** El archivo de anclas no se puede leer o tiene una línea que no se reconoce. La verificación lo informa como rota. */
public class AnchorsUnreadableException extends RuntimeException {

    public AnchorsUnreadableException(String message, Throwable cause) {
        super(message, cause);
    }
}
