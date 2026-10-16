package com.guajiranet.migradoronu.ejecucion.aplicacion;

import java.util.Map;

/**
 * Lo que GenieACS tiene de una ONU: su ID en GenieACS, el último Inform y el X_HW_SERVICELIST de cada conexión WAN
 * (ruta completa del parámetro → valor). Puede estar viejo: GenieACS solo lo actualiza cuando lo pide.
 */
public record EstadoServicioWan(String idDispositivo, long ultimoInformMs, Map<String, String> servicios, String reportaPorInternet) {

    /**
     * @param reportaPorInternet la IP de internet por la que se reporta a GenieACS (su TR-069 ya va por la WAN de internet),
     *                           o null si se reporta por la de gestión o no se sabe
     */
    public EstadoServicioWan {
        servicios = Map.copyOf(servicios);
    }

    public EstadoServicioWan(String idDispositivo, long ultimoInformMs, Map<String, String> servicios) {
        this(idDispositivo, ultimoInformMs, servicios, null);
    }

    /** La conexión del internet del cliente (INTERNET o ya TR069_INTERNET). */
    public java.util.Optional<String> rutaServicio() {
        return servicios.entrySet().stream().filter(e -> e.getValue().equals("INTERNET") || e.getValue().equals("TR069_INTERNET"))
                .map(Map.Entry::getKey).sorted().findFirst();
    }

    /** La conexión vieja de TR-069 (TR069 o ya OTHER). */
    public java.util.Optional<String> rutaTr069() {
        return servicios.entrySet().stream().filter(e -> e.getValue().equals("TR069") || e.getValue().equals("OTHER"))
                .map(Map.Entry::getKey).sorted().findFirst();
    }

    public boolean listo() {
        return rutaServicio().map(servicios::get).filter("TR069_INTERNET"::equals).isPresent()
                && rutaTr069().map(servicios::get).filter("OTHER"::equals).isPresent();
    }

    public String describir() {
        return servicios.isEmpty() ? "sin X_HW_SERVICELIST leído" : String.join(", ", servicios.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> "conexión " + conexion(e.getKey()) + " = " + e.getValue()).toList());
    }

    static String conexion(String ruta) {
        var m = java.util.regex.Pattern.compile("WANConnectionDevice\\.(\\d+)\\.").matcher(ruta);
        return m.find() ? m.group(1) : ruta;
    }
}
