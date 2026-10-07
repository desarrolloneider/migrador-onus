package com.guajiranet.migradoronu.acceso.dominio;

import java.util.Locale;
import java.util.regex.Pattern;

import com.guajiranet.migradoronu.comun.errores.ErrorDeNegocio;

/** El usuario con que se entra: único, sin espacios, en minúsculas (ESP-02). */
public final class NombreDeUsuario {

    private static final Pattern FORMATO = Pattern.compile("[a-z0-9._-]{3,60}");

    private NombreDeUsuario() {
    }

    /** Lleva a minúsculas y quita espacios a los lados; no valida. Sirve para buscar al iniciar sesión. */
    public static String normalizar(String texto) {
        return texto == null ? "" : texto.strip().toLowerCase(Locale.ROOT);
    }

    /** Normaliza y exige el formato. Sirve para crear usuarios. */
    public static String validar(String texto) {
        String normalizado = normalizar(texto);
        if (!FORMATO.matcher(normalizado).matches()) {
            throw ErrorDeNegocio.datosInvalidos(
                    "El usuario debe tener de 3 a 60 caracteres: letras, números, punto, guion o guion bajo, sin espacios.");
        }
        return normalizado;
    }
}
