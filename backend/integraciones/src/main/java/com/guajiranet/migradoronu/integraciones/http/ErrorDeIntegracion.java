package com.guajiranet.migradoronu.integraciones.http;

/** Un sistema externo no respondió, rechazó la petición o devolvió algo ilegible. El mensaje es para la pantalla. */
public class ErrorDeIntegracion extends RuntimeException {

    private final String sistema;

    public ErrorDeIntegracion(String sistema, String mensaje, Throwable causa) {
        super(mensaje, causa);
        this.sistema = sistema;
    }

    public String sistema() {
        return sistema;
    }
}
