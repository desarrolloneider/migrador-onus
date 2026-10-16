package com.guajiranet.migradoronu.ejecucion.aplicacion;

/** Una ONU tomada de la cola para ejecutar, con todo lo que se congeló al confirmar el lote (RN-37). */
public record TrabajoOnu(long ejecucionId, long loteId, String loteNumero, boolean simulacion, long confirmadoPor, String sn,
                         String oltCodigo, String tarjeta, int pon, int onuId, String perfilDestino, boolean guardar,
                         java.util.List<String> perfilesOrigen, String tipo) {

    /** El lote pone la IP de gestión DHCP en la VLAN de gestión (SmartOLT) en lugar de cambiar el perfil en la OLT. */
    public boolean esVlanGestion() {
        return "VLAN_GESTION".equals(tipo);
    }

    /** X_HW_SERVICELIST en GenieACS, quitar la VLAN 46 y volver a poner el perfil TR-069. */
    public boolean esServicioWan() {
        return "SERVICIO_WAN".equals(tipo);
    }

    /** Los 3 pasos seguidos (VLAN 46, GUAJIRANET1 y servicio WAN) antes de la siguiente ONU de la OLT. */
    public boolean esTresPasos() {
        return "TRES_PASOS".equals(tipo);
    }
}
