package com.guajiranet.migradoronu.ejecucion.aplicacion;

/**
 * La IP de gestión de una ONU tal como la muestra SmartOLT en este momento.
 *
 * @param idExterno el "ID externo del ONU" de SmartOLT, con el que se le mandan cambios
 * @param modo      "Inactive", "DHCP", "Static"…
 * @param vlan      la VLAN de gestión; null si no tiene
 * @param ip        la IP de gestión asignada; null si todavía no tiene
 */
public record EstadoGestionOnu(String idExterno, boolean enLinea, String modo, Integer vlan, String ip, boolean tr069) {

    public EstadoGestionOnu(String idExterno, boolean enLinea, String modo, Integer vlan, String ip) {
        this(idExterno, enLinea, modo, vlan, ip, false);
    }

    public boolean tieneDhcpEn(int vlanGestion) {
        return modo != null && modo.equalsIgnoreCase("DHCP") && vlan != null && vlan == vlanGestion;
    }

    public String describir() {
        return "gestión " + (modo == null ? "sin dato" : modo) + (vlan == null ? "" : " en VLAN " + vlan)
                + (ip == null || ip.isBlank() ? "" : " (IP " + ip + ")") + ", TR-069 " + (tr069 ? "activo" : "inactivo") + ", " + (enLinea ? "en línea" : "fuera de línea");
    }
}
