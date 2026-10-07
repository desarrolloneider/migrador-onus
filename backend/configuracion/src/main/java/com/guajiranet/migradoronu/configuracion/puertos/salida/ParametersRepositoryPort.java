package com.guajiranet.migradoronu.configuracion.puertos.salida;

import java.time.Instant;
import java.util.Map;

import com.guajiranet.migradoronu.configuracion.dominio.ValorGuardado;

/** La tabla parametros. */
public interface ParametersRepositoryPort {

    Map<String, ValorGuardado> todos();

    void guardar(String clave, Object valor, long usuarioId, Instant ahora);

    /** Cambia cada vez que cambia un parámetro o las tarjetas de una OLT. */
    long revision();
}
