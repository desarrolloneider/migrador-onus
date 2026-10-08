package com.guajiranet.migradoronu.conciliacion.aplicacion;

import java.util.List;

/** Marca obligatoria y filtros opcionales por OLT, tarjeta y puerto PON (ESP-03). */
public record ClassifyOnusCommand(String marca, String oltCodigo, String tarjeta, Integer pon, List<String> perfilesOrigen,
                                  List<String> sns) {

    /** Las ONUs sin perfil TR-069, cuando se escogen como origen. */
    public static final String SIN_PERFIL = "SIN_PERFIL";

    public ClassifyOnusCommand(String marca, String oltCodigo, String tarjeta, Integer pon) {
        this(marca, oltCodigo, tarjeta, pon, null, null);
    }


    public ClassifyOnusCommand {
        if (marca == null || marca.isBlank()) {
            throw new IllegalArgumentException("La marca es obligatoria (ESP-03)");
        }
        if (tarjeta != null && oltCodigo == null) {
            throw new IllegalArgumentException("Para escoger tarjeta hay que escoger OLT (ESP-03)");
        }
        if (pon != null && tarjeta == null) {
            throw new IllegalArgumentException("Para escoger puerto PON hay que escoger tarjeta (ESP-03)");
        }
        if (pon != null && pon < 0) {
            throw new IllegalArgumentException("El puerto PON no puede ser negativo");
        }
        perfilesOrigen = perfilesOrigen == null || perfilesOrigen.isEmpty() ? null : List.copyOf(perfilesOrigen);
        sns = sns == null || sns.isEmpty() ? null : List.copyOf(sns);
    }
}
