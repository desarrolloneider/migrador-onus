package com.guajiranet.migradoronu.lotes.dominio;

import java.util.List;

/**
 * Marca obligatoria y filtros opcionales de un lote (ESP-03). Zona y nodo no se usan (respuesta del 3 de octubre).
 *
 * @param perfilesOrigen los perfiles que se cambian ("SIN_PERFIL" para las ONUs sin perfil); null: el configurado
 * @param sns            las ONUs escogidas una por una en la pantalla; null: todas las que cumplan los filtros
 */
public record BatchFilters(String marca, String oltCodigo, String tarjeta, Integer pon, List<String> perfilesOrigen, List<String> sns,
                           String tipo) {

    /** El lote cambia el perfil TR-069 en la OLT (el de siempre). */
    public static final String PERFIL_TR069 = "PERFIL_TR069";
    /** El lote pone la IP de gestión DHCP en la VLAN de gestión (46) en SmartOLT. */
    public static final String VLAN_GESTION = "VLAN_GESTION";

    public BatchFilters(String marca, String oltCodigo, String tarjeta, Integer pon, List<String> perfilesOrigen, List<String> sns) {
        this(marca, oltCodigo, tarjeta, pon, perfilesOrigen, sns, null);
    }

    public boolean esVlanGestion() {
        return VLAN_GESTION.equals(tipo);
    }

    public BatchFilters(String marca, String oltCodigo, String tarjeta, Integer pon) {
        this(marca, oltCodigo, tarjeta, pon, null, null, null);
    }


    public BatchFilters {
        if (marca == null || marca.isBlank()) {
            throw new IllegalArgumentException("La marca es obligatoria (ESP-03)");
        }
        if (tarjeta != null && oltCodigo == null) {
            throw new IllegalArgumentException("Para escoger tarjeta hay que escoger OLT (ESP-03)");
        }
        if (pon != null && tarjeta == null) {
            throw new IllegalArgumentException("Para escoger puerto PON hay que escoger tarjeta (ESP-03)");
        }
        perfilesOrigen = perfilesOrigen == null || perfilesOrigen.isEmpty() ? null : List.copyOf(perfilesOrigen);
        sns = sns == null || sns.isEmpty() ? null : List.copyOf(sns);
        tipo = tipo == null || tipo.isBlank() ? PERFIL_TR069 : tipo.strip().toUpperCase(java.util.Locale.ROOT);
        if (!tipo.equals(PERFIL_TR069) && !tipo.equals(VLAN_GESTION)) {
            throw new IllegalArgumentException("Tipo de lote desconocido: " + tipo);
        }
    }
}
