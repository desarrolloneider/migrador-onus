package com.guajiranet.migradoronu.procedimientos.dominio;

/** Resultado de la lectura de verificación de un paso: qué se esperaba y qué se encontró (RN-10, ESP-09). */
public record Verification(boolean matches, String expected, String found) {

    public static Verification ok(String expected, String found) {
        return new Verification(true, expected, found);
    }

    public static Verification mismatch(String expected, String found) {
        return new Verification(false, expected, found);
    }
}
