package com.guajiranet.migradoronu.acceso.puertos.entrada;

import com.guajiranet.migradoronu.acceso.dominio.ResultadoAutenticacion;

/** Iniciar y cerrar sesión (ESP-01). Todo intento queda en la auditoría (RN-11). */
public interface AuthenticatePort {

    ResultadoAutenticacion autenticar(String usuario, String clave, String ipOrigen);

    void registrarCierre(long usuarioId, String ipOrigen);
}
