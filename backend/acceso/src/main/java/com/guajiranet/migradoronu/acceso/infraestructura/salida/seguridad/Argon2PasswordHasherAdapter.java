package com.guajiranet.migradoronu.acceso.infraestructura.salida.seguridad;

import java.nio.CharBuffer;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import com.guajiranet.migradoronu.acceso.puertos.salida.PasswordHasherPort;

/** Argon2id con los parámetros recomendados por Spring Security (arquitectura 11.1, RNF-07). */
public class Argon2PasswordHasherAdapter implements PasswordHasherPort {

    private final Argon2PasswordEncoder codificador = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

    @Override
    public String huella(char[] clave) {
        return codificador.encode(CharBuffer.wrap(clave));
    }

    @Override
    public boolean coincide(char[] clave, String huella) {
        return huella != null && codificador.matches(CharBuffer.wrap(clave), huella);
    }
}
