package com.guajiranet.migradoronu.configuracion.puertos.entrada;

import java.util.Collection;

import com.guajiranet.migradoronu.configuracion.dominio.OltSettings;

/** Cambiar las tarjetas permitidas de una OLT (RN-14). Las credenciales no se ven ni se cambian aquí (RN-25). */
public interface ManageOltSettingsPort {

    OltSettings cambiarTarjetas(long oltId, Collection<String> tarjetas, long usuarioId, String ipOrigen);
}
