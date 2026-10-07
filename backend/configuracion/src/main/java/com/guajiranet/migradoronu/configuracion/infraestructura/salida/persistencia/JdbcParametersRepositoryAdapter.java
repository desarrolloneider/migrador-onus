package com.guajiranet.migradoronu.configuracion.infraestructura.salida.persistencia;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.jdbc.core.simple.JdbcClient;

import com.guajiranet.migradoronu.configuracion.dominio.ValorGuardado;
import com.guajiranet.migradoronu.configuracion.puertos.salida.ParametersRepositoryPort;

import tools.jackson.databind.json.JsonMapper;

/** La tabla parametros. El valor es JSONB: número, texto o NULL (sin valor). */
public class JdbcParametersRepositoryAdapter implements ParametersRepositoryPort {

    private final JdbcClient jdbc;
    private final JsonMapper json = JsonMapper.builder().build();

    public JdbcParametersRepositoryAdapter(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    @Override
    public Map<String, ValorGuardado> todos() {
        Map<String, ValorGuardado> valores = new LinkedHashMap<>();
        jdbc.sql("SELECT clave, valor::text AS valor, actualizado_por, actualizado_en FROM parametros ORDER BY clave")
                .query(rs -> {
                    String crudo = rs.getString("valor");
                    long por = rs.getLong("actualizado_por");
                    Long actualizadoPor = rs.wasNull() ? null : por;
                    valores.put(rs.getString("clave"), new ValorGuardado(
                            crudo == null ? null : json.readValue(crudo, Object.class),
                            actualizadoPor,
                            rs.getObject("actualizado_en", OffsetDateTime.class).toInstant()));
                });
        return valores;
    }

    @Override
    public void guardar(String clave, Object valor, long usuarioId, Instant ahora) {
        jdbc.sql("UPDATE parametros SET valor = CAST(? AS jsonb), actualizado_por = ?, actualizado_en = ? WHERE clave = ?")
                .params(valor == null ? null : json.writeValueAsString(valor), usuarioId, Timestamp.from(ahora), clave)
                .update();
    }

    @Override
    public long revision() {
        return jdbc.sql("""
                SELECT COALESCE(EXTRACT(EPOCH FROM GREATEST(
                    (SELECT max(actualizado_en) FROM parametros), (SELECT max(actualizado_en) FROM olts))) * 1000, 0)::bigint
                """).query(Long.class).single();
    }
}
