package com.guajiranet.migradoronu.ejecucion.puertos.salida;

import java.util.Optional;

import com.guajiranet.migradoronu.ejecucion.aplicacion.EstadoGestionOnu;

/** La IP de gestión de una ONU en SmartOLT (procedimiento del NOC "VLAN 46"). Lo implementa ensamblaje. */
public interface GestionSmartOltPort {

    /** Lo que SmartOLT muestra ahora de la ONU, leído en el momento (sin caché). Vacío si SmartOLT no la tiene. */
    Optional<EstadoGestionOnu> leer(String sn);

    /** IP de gestión por DHCP en esa VLAN. Se manda una sola vez: si falla, no se repite a ciegas. */
    void activarDhcp(String idExterno, int vlan);

    /**
     * Activa el TR-069 de la ONU por la IP de gestión con ese perfil (lo que SmartOLT hace a mano: "TR069 Profile:
     * SmartOLT vía IP de gestión"). Sin esto la ONU no pide IP por la VLAN de gestión.
     */
    void activarTr069(String idExterno, String perfil);

    /** Reinicia la ONU (el cliente queda sin servicio cerca de un minuto). Al prender se reporta enseguida a GenieACS. */
    void reiniciar(String idExterno);

    /** IP de gestión en Inactiva (quita la VLAN de gestión). SmartOLT también le quita el perfil TR-069 en la OLT. */
    void desactivarGestion(String idExterno);
}
