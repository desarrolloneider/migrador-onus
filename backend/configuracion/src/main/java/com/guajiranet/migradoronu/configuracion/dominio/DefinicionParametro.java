package com.guajiranet.migradoronu.configuracion.dominio;

import java.util.List;
import java.util.Objects;

import com.guajiranet.migradoronu.comun.errores.ErrorDeNegocio;

/** Qué es un parámetro de la spec 7.5 y qué valores admite. El valor vive en la base; la definición, aquí. */
public record DefinicionParametro(String clave, String nombre, String grupo, Tipo tipo, String unidad, Integer minimo,
                                  Integer maximo, List<Opcion> opciones, String referencia, String descripcion,
                                  boolean editable, boolean admiteSinValor) {

    public enum Tipo { ENTERO, TEXTO, OPCION }

    public record Opcion(String valor, String etiqueta) {
    }

    public DefinicionParametro {
        Objects.requireNonNull(clave, "clave");
        Objects.requireNonNull(tipo, "tipo");
        opciones = opciones == null ? List.of() : List.copyOf(opciones);
    }

    /** Lleva el valor a su forma guardada (Integer, String o null) o lanza el mensaje de la spec (ESP-15). */
    public Object validar(Object valor) {
        if (valor == null || (valor instanceof String texto && texto.isBlank())) {
            if (!admiteSinValor) {
                throw ErrorDeNegocio.datosInvalidos(nombre + " no puede quedar sin valor.");
            }
            return null;
        }
        return switch (tipo) {
            case ENTERO -> {
                Integer numero = aEntero(valor);
                if (numero == null || (minimo != null && numero < minimo) || (maximo != null && numero > maximo)) {
                    throw ErrorDeNegocio.datosInvalidos(nombre + " debe estar entre " + minimo + " y " + maximo + ".");
                }
                yield numero;
            }
            case OPCION -> {
                String texto = String.valueOf(valor);
                if (opciones.stream().noneMatch(o -> o.valor().equals(texto))) {
                    throw ErrorDeNegocio.datosInvalidos(nombre + " no admite el valor " + texto + ".");
                }
                yield texto;
            }
            case TEXTO -> {
                String texto = String.valueOf(valor).strip();
                if (clave.equals("perfil_tr069") && texto.chars().anyMatch(Character::isWhitespace)) {
                    throw ErrorDeNegocio.datosInvalidos("El nombre del perfil no puede tener espacios.");
                }
                if (texto.length() > 200) {
                    throw ErrorDeNegocio.datosInvalidos(nombre + " admite hasta 200 caracteres.");
                }
                yield texto;
            }
        };
    }

    private static Integer aEntero(Object valor) {
        if (valor instanceof Number numero && numero.doubleValue() == Math.rint(numero.doubleValue())) {
            return numero.intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(valor).strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
