package com.guajiranet.migradoronu.ejecucion.puertos.entrada;

import java.util.Map;

/**
 * El perfil TR-069 de cada ONU según la propia OLT (bound-info de cada perfil). Solo lectura. La llave es
 * {@code "0/0/4:16"} (frame/slot/puerto:ONT) y el valor el nombre del perfil. Las ONUs sin perfil no aparecen.
 */
public interface ReadOltProfilesPort {

    Map<String, String> perfilesPorOnu(String oltCodigo);
}
