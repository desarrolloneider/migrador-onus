package com.guajiranet.migradoronu.conciliacion.aplicacion;

import java.util.List;

/** Marca obligatoria y filtros opcionales por OLT, tarjeta y puerto PON (ESP-03). */
public record ClassifyOnusCommand(String marca, String oltCodigo, String tarjeta, Integer pon, List<String> perfilesOrigen,
                                  List<String> sns, String tipo) {

    /** El lote pone la IP de gestión DHCP en la VLAN de gestión (SmartOLT) en lugar de cambiar el perfil TR-069. */
    public static final String VLAN_GESTION = "VLAN_GESTION";

    public ClassifyOnusCommand(String marca, String oltCodigo, String tarjeta, Integer pon, List<String> perfilesOrigen, List<String> sns) {
        this(marca, oltCodigo, tarjeta, pon, perfilesOrigen, sns, null);
    }

    public boolean esVlanGestion() {
        return VLAN_GESTION.equals(tipo);
    }

    /** Lote "Servicio WAN": X_HW_SERVICELIST en GenieACS y quitar la VLAN de gestión. */
    public static final String SERVICIO_WAN = "SERVICIO_WAN";

    public boolean esServicioWan() {
        return SERVICIO_WAN.equals(tipo);
    }

    /** Lote "Los 3 pasos": VLAN 46, GUAJIRANET1 y servicio WAN seguidos con cada ONU. */
    public static final String TRES_PASOS = "TRES_PASOS";

    public boolean esTresPasos() {
        return TRES_PASOS.equals(tipo);
    }

    /** Las ONUs sin perfil TR-069, cuando se escogen como origen. */
    public static final String SIN_PERFIL = "SIN_PERFIL";

    public ClassifyOnusCommand(String marca, String oltCodigo, String tarjeta, Integer pon) {
        this(marca, oltCodigo, tarjeta, pon, null, null, null);
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
