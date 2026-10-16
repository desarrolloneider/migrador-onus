package com.guajiranet.migradoronu.ejecucion.infraestructura.salida.persistencia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.jdbc.core.simple.JdbcClient;

import com.guajiranet.migradoronu.ejecucion.aplicacion.EjecucionVista;
import com.guajiranet.migradoronu.ejecucion.puertos.salida.LecturasDeEjecucionPort;

/** Lecturas de ejecuciones_onu unidas a lotes y lote_items (solo lectura de esas dos). */
public class JdbcLecturasDeEjecucionAdapter implements LecturasDeEjecucionPort {

    private static final String COLUMNAS = """
            SELECT e.id, e.lote_id, l.numero, l.simulacion, e.sn, i.cliente, i.ip, i.contrato, i.modelo, i.olt_codigo, i.tarjeta,
                   i.pon, i.onu_id, e.orden, e.estado, e.paso_actual, e.codigo_fallo, e.mensaje_fallo, e.iniciada_en, e.terminada_en,
                   e.revisada, e.revisada_por, e.revisada_en, e.nota_revision, e.resuelta_en_lote_id, lr.numero AS resuelta_en_numero
            FROM ejecuciones_onu e JOIN lotes l ON l.id = e.lote_id JOIN lote_items i ON i.id = e.lote_item_id
            LEFT JOIN lotes lr ON lr.id = e.resuelta_en_lote_id
            """;

    private final JdbcClient jdbc;

    public JdbcLecturasDeEjecucionAdapter(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    @Override
    public List<EjecucionVista> delLote(long loteId) {
        return jdbc.sql(COLUMNAS + " WHERE e.lote_id = ? ORDER BY e.orden").param(loteId).query(JdbcLecturasDeEjecucionAdapter::fila).list();
    }

    @Override
    public Optional<EjecucionVista> porId(long ejecucionId) {
        return jdbc.sql(COLUMNAS + " WHERE e.id = ?").param(ejecucionId).query(JdbcLecturasDeEjecucionAdapter::fila).optional();
    }

    @Override
    public List<EjecucionVista> fallidasSinRevisar() {
        return jdbc.sql(COLUMNAS + " WHERE e.estado = 'FALLIDA' AND NOT e.revisada AND e.resuelta_en_lote_id IS NULL ORDER BY e.terminada_en DESC")
                .query(JdbcLecturasDeEjecucionAdapter::fila).list();
    }

    @Override
    public boolean marcarRevisada(long ejecucionId, long usuarioId, String nota, Instant ahora) {
        return jdbc.sql("""
                        UPDATE ejecuciones_onu SET revisada = TRUE, revisada_por = ?, revisada_en = ?, nota_revision = ?
                        WHERE id = ? AND estado = 'FALLIDA' AND NOT revisada
                        """)
                .params(usuarioId, Timestamp.from(ahora), nota, ejecucionId)
                .update() == 1;
    }

    @Override
    public int marcarTodasRevisadas(long usuarioId, String nota, Instant ahora) {
        return jdbc.sql("""
                        UPDATE ejecuciones_onu SET revisada = TRUE, revisada_por = ?, revisada_en = ?, nota_revision = ?
                        WHERE estado = 'FALLIDA' AND NOT revisada AND resuelta_en_lote_id IS NULL
                        """)
                .params(usuarioId, Timestamp.from(ahora), nota)
                .update();
    }

    private static EjecucionVista fila(ResultSet rs, int n) throws SQLException {
        long revisadaPor = rs.getLong("revisada_por");
        Long porQuien = rs.wasNull() ? null : revisadaPor;
        return new EjecucionVista(rs.getLong("id"), rs.getLong("lote_id"), rs.getString("numero"), rs.getBoolean("simulacion"),
                rs.getString("sn"), rs.getString("cliente"), rs.getString("ip"), rs.getString("contrato"), rs.getString("modelo"),
                rs.getString("olt_codigo"), rs.getString("tarjeta"), rs.getInt("pon"), rs.getInt("onu_id"), rs.getInt("orden"),
                rs.getString("estado"), rs.getString("paso_actual"), rs.getString("codigo_fallo"), rs.getString("mensaje_fallo"),
                instante(rs, "iniciada_en"), instante(rs, "terminada_en"), rs.getBoolean("revisada"), porQuien,
                instante(rs, "revisada_en"), rs.getString("nota_revision"),
                rs.getObject("resuelta_en_lote_id", Long.class), rs.getString("resuelta_en_numero"));
    }

    private static Instant instante(ResultSet rs, String columna) throws SQLException {
        OffsetDateTime v = rs.getObject(columna, OffsetDateTime.class);
        return v == null ? null : v.toInstant();
    }
}
