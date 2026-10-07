package com.guajiranet.migradoronu.configuracion.puertos.entrada;

import com.guajiranet.migradoronu.configuracion.dominio.Parametro;

/** Cambiar un parámetro: nunca con un lote en ejecución (RN-28); queda en la auditoría con valor anterior y nuevo. */
public interface ConfigureParametersPort {

    Parametro cambiar(String clave, Object valor, long usuarioId, String ipOrigen);
}
