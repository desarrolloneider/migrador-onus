package com.guajiranet.migradoronu.configuracion.infraestructura.salida.persistencia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.jdbc.core.simple.JdbcClient;

import com.guajiranet.migradoronu.configuracion.dominio.AllowedBoards;
import com.guajiranet.migradoronu.configuracion.dominio.ConsoleProtocol;
import com.guajiranet.migradoronu.configuracion.dominio.OltSettings;
import com.guajiranet.migradoronu.configuracion.puertos.salida.OltSettingsRepositoryPort;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** La tabla olts. No tiene credenciales: viven en el servidor (arquitectura 11.1). */
public class JdbcOltSettingsRepositoryAdapter implements OltSettingsRepositoryPort {

    private static final String COLUMNAS =
            "SELECT id, codigo, nombre, direccion, puerto, protocolo, tarjetas_permitidas::text AS tarjetas, activa FROM olts";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<String>> LISTA = new TypeReference<>() { };

    private final JdbcClient jdbc;

    public JdbcOltSettingsRepositoryAdapter(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    @Override
    public List<OltSettings> listar() {
        return jdbc.sql(COLUMNAS + " ORDER BY codigo").query(JdbcOltSettingsRepositoryAdapter::fila).list();
    }

    @Override
    public Optional<OltSettings> porId(long id) {
        return jdbc.sql(COLUMNAS + " WHERE id = ?").param(id).query(JdbcOltSettingsRepositoryAdapter::fila).optional();
    }

    @Override
    public Optional<OltSettings> porCodigo(String codigo) {
        return jdbc.sql(COLUMNAS + " WHERE codigo = ?").param(codigo).query(JdbcOltSettingsRepositoryAdapter::fila).optional();
    }

    @Override
    public void guardarTarjetas(long id, AllowedBoards tarjetas, long usuarioId, Instant ahora) {
        jdbc.sql("UPDATE olts SET tarjetas_permitidas = CAST(? AS jsonb), actualizado_por = ?, actualizado_en = ? WHERE id = ?")
                .params(JSON.writeValueAsString(List.copyOf(tarjetas.values())), usuarioId, Timestamp.from(ahora), id)
                .update();
    }

    private static OltSettings fila(ResultSet rs, int n) throws SQLException {
        int puerto = rs.getInt("puerto");
        Integer puertoONulo = rs.wasNull() ? null : puerto;
        String protocolo = rs.getString("protocolo");
        List<String> tarjetas = JSON.readValue(rs.getString("tarjetas"), LISTA);
        return new OltSettings(rs.getLong("id"), rs.getString("codigo"), rs.getString("nombre"), rs.getString("direccion"),
                puertoONulo, protocolo == null ? null : ConsoleProtocol.valueOf(protocolo),
                new AllowedBoards(new LinkedHashSet<>(tarjetas)), rs.getBoolean("activa"));
    }
}
