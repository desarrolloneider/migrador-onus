package com.guajiranet.migradoronu.lotes.infraestructura.salida.persistencia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.jdbc.core.simple.JdbcClient;

import com.guajiranet.migradoronu.lotes.aplicacion.BatchView;
import com.guajiranet.migradoronu.lotes.dominio.BatchMode;
import com.guajiranet.migradoronu.lotes.dominio.BatchStatus;
import com.guajiranet.migradoronu.lotes.puertos.entrada.QueryBatchPort;

/** Consultas de la tabla lotes. Solo lectura: armar y confirmar lotes llega con la integración de SmartOLT. */
public class JdbcQueryBatchAdapter implements QueryBatchPort {

    private static final String COLUMNAS = "SELECT id, numero, modo, simulacion, estado, marca, creado_en FROM lotes ";

    private final JdbcClient jdbc;

    public JdbcQueryBatchAdapter(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    @Override
    public Optional<BatchView> findById(long loteId) {
        return jdbc.sql(COLUMNAS + "WHERE id = ?").param(loteId).query(JdbcQueryBatchAdapter::fila).optional();
    }

    @Override
    public Optional<BatchView> findByNumero(String numero) {
        return jdbc.sql(COLUMNAS + "WHERE numero = ?").param(numero.strip().toUpperCase()).query(JdbcQueryBatchAdapter::fila).optional();
    }

    @Override
    public Optional<BatchView> findRunning() {
        return jdbc.sql(COLUMNAS + "WHERE estado IN ('EN_EJECUCION', 'DETENIENDOSE') ORDER BY creado_en DESC LIMIT 1")
                .query(JdbcQueryBatchAdapter::fila).optional();
    }

    @Override
    public List<BatchView> findRecent(int limit) {
        return jdbc.sql(COLUMNAS + "ORDER BY creado_en DESC LIMIT ?").param(Math.max(1, Math.min(limit, 200)))
                .query(JdbcQueryBatchAdapter::fila).list();
    }

    private static BatchView fila(ResultSet rs, int n) throws SQLException {
        return new BatchView(rs.getLong("id"), rs.getString("numero"), BatchMode.valueOf(rs.getString("modo")),
                rs.getBoolean("simulacion"), BatchStatus.valueOf(rs.getString("estado")), rs.getString("marca"),
                rs.getObject("creado_en", OffsetDateTime.class).toInstant());
    }
}
