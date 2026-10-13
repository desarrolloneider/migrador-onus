package com.guajiranet.migradoronu.conciliacion.dominio;

import java.util.List;

/**
 * La gestión de una ONU según SmartOLT (procedimiento del NOC "VLAN 46 + X_HW_SERVICELIST"). Solo lectura.
 *
 * @param modoGestion   el modo de la IP de gestión ("Inactive", "DHCP", "Static"…); null si SmartOLT no lo trae
 * @param vlanGestion   la VLAN de la IP de gestión; null si no tiene
 * @param vlansServicio las VLAN de sus service-ports
 * @param tr069         si SmartOLT tiene activado TR-069 en la ONU
 */
public record GestionOnu(String modoGestion, Integer vlanGestion, List<Integer> vlansServicio, boolean tr069) {

    public GestionOnu {
        vlansServicio = vlansServicio == null ? List.of() : List.copyOf(vlansServicio);
    }

    /** ¿Tiene esa VLAN, como gestión o en algún service-port? */
    public boolean tieneVlan(int vlan) {
        return (vlanGestion != null && vlanGestion == vlan) || vlansServicio.contains(vlan);
    }

    public boolean gestionActiva() {
        return modoGestion != null && !modoGestion.isBlank() && !modoGestion.equalsIgnoreCase("Inactive");
    }
}
