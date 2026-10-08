package com.guajiranet.migradoronu.configuracion.puertos.entrada;

import java.util.Collection;

import com.guajiranet.migradoronu.configuracion.dominio.OltSettings;

/** Cambiar las tarjetas permitidas de una OLT (RN-14). Las credenciales no se ven ni se cambian aquí (RN-25). */
public interface ManageOltSettingsPort {

    /** Dirección, puerto y protocolo de la consola. Si cambia la dirección se olvida la huella fijada. */
    OltSettings cambiarAcceso(long oltId, String direccion, Integer puerto, String protocolo, long usuarioId, String ipOrigen);

    /** Fija la huella SSH que presentó la OLT al probar la conexión. */
    void fijarHuella(long oltId, String huella, long usuarioId, String ipOrigen);

    OltSettings cambiarTarjetas(long oltId, Collection<String> tarjetas, long usuarioId, String ipOrigen);
}
