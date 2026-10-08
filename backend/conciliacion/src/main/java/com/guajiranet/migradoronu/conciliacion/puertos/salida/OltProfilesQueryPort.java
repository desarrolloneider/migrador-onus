package com.guajiranet.migradoronu.conciliacion.puertos.salida;

import java.util.Map;
import java.util.Optional;

/**
 * El perfil TR-069 de cada ONU según la OLT (no según SmartOLT, que no lo trae). Llave {@code "0/0/4:16"}, valor el
 * nombre del perfil. Vacío si la OLT no tiene acceso por consola o no se pudo leer. Lo implementa ensamblaje.
 */
public interface OltProfilesQueryPort {

    Optional<Map<String, String>> perfiles(String oltCodigo);
}
