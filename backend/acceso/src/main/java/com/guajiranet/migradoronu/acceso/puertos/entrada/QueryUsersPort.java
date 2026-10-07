package com.guajiranet.migradoronu.acceso.puertos.entrada;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/** Consultas de usuarios para el resto de la herramienta. */
public interface QueryUsersPort {

    /** Un usuario desactivado con sesión abierta se rechaza en su siguiente acción (ESP-02). */
    boolean activo(long usuarioId);

    Map<Long, String> nombres(Collection<Long> ids);

    Set<Long> idsQueCoinciden(String texto);
}
