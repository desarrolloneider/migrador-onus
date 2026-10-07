package com.guajiranet.migradoronu.acceso.puertos.salida;

/** Huella lenta y resistente de las contraseñas (RNF-07). La contraseña nunca se guarda ni se muestra tal cual. */
public interface PasswordHasherPort {

    String huella(char[] clave);

    boolean coincide(char[] clave, String huella);
}
