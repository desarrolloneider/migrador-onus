package com.guajiranet.migradoronu.ejecucion.puertos.salida;

import java.util.Optional;

import com.guajiranet.migradoronu.ejecucion.aplicacion.EstadoGestionOnu;

/** La IP de gestión de una ONU en SmartOLT (procedimiento del NOC "VLAN 46"). Lo implementa ensamblaje. */
public interface GestionSmartOltPort {

    /** Lo que SmartOLT muestra ahora de la ONU, leído en el momento (sin caché). Vacío si SmartOLT no la tiene. */
    Optional<EstadoGestionOnu> leer(String sn);

    /** IP de gestión por DHCP en esa VLAN. Se manda una sola vez: si falla, no se repite a ciegas. */
    void activarDhcp(String idExterno, int vlan);
}
