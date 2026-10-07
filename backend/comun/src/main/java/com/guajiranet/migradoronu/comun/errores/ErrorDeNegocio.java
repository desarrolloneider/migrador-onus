package com.guajiranet.migradoronu.comun.errores;

import java.util.Objects;

/**
 * Un error que se le muestra a la persona tal cual: su mensaje ya es el de la spec (qué pasó, por qué y qué dato lo
 * demuestra, RN-20). El proceso web lo convierte en Problem Details con el estado HTTP de su tipo.
 */
public class ErrorDeNegocio extends RuntimeException {

    public enum Tipo {
        /** 422: los datos no cumplen una validación. */
        DATOS_INVALIDOS,
        /** 404: lo que se busca no existe. */
        NO_ENCONTRADO,
        /** 409: el estado actual no permite la acción. */
        CONFLICTO,
        /** 423: el usuario está bloqueado. */
        BLOQUEADO,
        /** 401: sin sesión o credenciales incorrectas. */
        SIN_SESION,
        /** 503: falta una pieza para poder hacerlo (por ejemplo, un sistema externo sin configurar). */
        NO_DISPONIBLE
    }

    private final Tipo tipo;

    public ErrorDeNegocio(Tipo tipo, String mensaje) {
        super(Objects.requireNonNull(mensaje, "mensaje"));
        this.tipo = Objects.requireNonNull(tipo, "tipo");
    }

    public static ErrorDeNegocio datosInvalidos(String mensaje) {
        return new ErrorDeNegocio(Tipo.DATOS_INVALIDOS, mensaje);
    }

    public static ErrorDeNegocio noEncontrado(String mensaje) {
        return new ErrorDeNegocio(Tipo.NO_ENCONTRADO, mensaje);
    }

    public static ErrorDeNegocio conflicto(String mensaje) {
        return new ErrorDeNegocio(Tipo.CONFLICTO, mensaje);
    }

    public static ErrorDeNegocio noDisponible(String mensaje) {
        return new ErrorDeNegocio(Tipo.NO_DISPONIBLE, mensaje);
    }

    public Tipo tipo() {
        return tipo;
    }
}
