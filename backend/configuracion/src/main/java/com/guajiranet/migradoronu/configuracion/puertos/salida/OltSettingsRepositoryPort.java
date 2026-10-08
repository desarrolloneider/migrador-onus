package com.guajiranet.migradoronu.configuracion.puertos.salida;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.guajiranet.migradoronu.configuracion.dominio.AllowedBoards;
import com.guajiranet.migradoronu.configuracion.dominio.OltSettings;

/** La tabla olts. Sin credenciales. */
public interface OltSettingsRepositoryPort {

    List<OltSettings> listar();

    Optional<OltSettings> porId(long id);

    Optional<OltSettings> porCodigo(String codigo);

    void guardarAcceso(long id, String direccion, Integer puerto, String protocolo, long usuarioId, Instant ahora);

    Optional<String> huella(String codigo);

    void guardarHuella(long id, String huella, long usuarioId, Instant ahora);

    void guardarTarjetas(long id, AllowedBoards tarjetas, long usuarioId, Instant ahora);
}
