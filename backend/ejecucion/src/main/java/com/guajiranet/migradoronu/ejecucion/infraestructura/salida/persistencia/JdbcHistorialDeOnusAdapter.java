package com.guajiranet.migradoronu.ejecucion.infraestructura.salida.persistencia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.jdbc.core.simple.JdbcClient;

import com.guajiranet.migradoronu.ejecucion.aplicacion.HistorialDeOnus.Entrada;
import com.guajiranet.migradoronu.ejecucion.aplicacion.HistorialDeOnus.Filtros;
import com.guajiranet.migradoronu.ejecucion.aplicacion.HistorialDeOnus.Intento;
import com.guajiranet.migradoronu.ejecucion.aplicacion.HistorialDeOnus.Pagina;
import com.guajiranet.migradoronu.ejecucion.puertos.salida.HistorialDeOnusPort;

/** El historial por ONU sobre ejecuciones_onu, lotes y lote_items (solo lectura). */
public class JdbcHistorialDeOnusAdapter implements HistorialDeOnusPort {

    /** Resultado visible de una ejecución. Una excluida sin código es una ONU que ya tenía el perfil nuevo. */
    private static final String RESULTADO = """
            CASE e.estado WHEN 'COMPLETADA' THEN 'CORREGIDA' WHEN 'FALLIDA' THEN 'FALLIDA'
                 WHEN 'EXCLUIDA_AL_EJECUTAR' THEN CASE WHEN e.codigo_fallo IS NULL THEN 'YA_TENIA_PERFIL' ELSE 'EXCLUIDA' END
                 ELSE e.estado END
            """;

    /** La última ejecución real de cada SN. */
    private static final String ULTIMAS = """
            WITH ultimas AS (
                SELECT DISTINCT ON (e.sn) e.sn, i.ip, i.olt_codigo, i.tarjeta, i.pon, i.onu_id, %s AS resultado,
                       CASE WHEN e.estado = 'FALLIDA' THEN e.revisada END AS revisada, e.codigo_fallo, e.mensaje_fallo,
                       e.lote_id, l.numero, coalesce(e.terminada_en, e.iniciada_en, l.creado_en) AS fecha
                FROM ejecuciones_onu e JOIN lotes l ON l.id = e.lote_id JOIN lote_items i ON i.id = e.lote_item_id
                WHERE NOT l.simulacion AND e.estado IN ('COMPLETADA', 'FALLIDA', 'EXCLUIDA_AL_EJECUTAR')
                ORDER BY e.sn, coalesce(e.terminada_en, e.iniciada_en, l.creado_en) DESC
            )
            """.formatted(RESULTADO);

    private final JdbcClient jdbc;

    public JdbcHistorialDeOnusAdapter(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    @Override
    public Pagina buscar(Filtros f) {
        StringBuilder donde = new StringBuilder(" WHERE TRUE");
        List<Object> parametros = new ArrayList<>();
        if (f.sn() != null) {
            donde.append(" AND sn ILIKE ?");
            parametros.add("%" + f.sn() + "%");
        }
        if (f.olt() != null) {
            donde.append(" AND olt_codigo = ?");
            parametros.add(f.olt());
        }
        if (f.tarjeta() != null) {
            donde.append(" AND tarjeta = ?");
            parametros.add(f.tarjeta());
        }
        if (f.desde() != null) {
            donde.append(" AND fecha >= ?");
            parametros.add(Timestamp.from(f.desde()));
        }
        if (f.hasta() != null) {
            donde.append(" AND fecha <= ?");
            parametros.add(Timestamp.from(f.hasta()));
        }
        // Los totales por resultado respetan todos los filtros salvo el de resultado.
        Map<String, Long> totales = new LinkedHashMap<>();
        for (String r : List.of("CORREGIDA", "FALLIDA", "EXCLUIDA", "YA_TENIA_PERFIL")) {
            totales.put(r, 0L);
        }
        jdbc.sql(ULTIMAS + "SELECT resultado, count(*) AS n FROM ultimas" + donde + " GROUP BY resultado").params(parametros)
                .query(rs -> {
                    totales.put(rs.getString("resultado"), rs.getLong("n"));
                });
        if (f.resultado() != null) {
            donde.append(" AND resultado = ?");
            parametros.add(f.resultado());
        }
        long total = jdbc.sql(ULTIMAS + "SELECT count(*) FROM ultimas" + donde).params(parametros).query(Long.class).single();
        List<Object> conPagina = new ArrayList<>(parametros);
        conPagina.add(f.tamano());
        conPagina.add((long) f.pagina() * f.tamano());
        List<Entrada> entradas = jdbc.sql(ULTIMAS + "SELECT * FROM ultimas" + donde + " ORDER BY fecha DESC LIMIT ? OFFSET ?")
                .params(conPagina).query(JdbcHistorialDeOnusAdapter::entrada).list();
        return new Pagina(entradas, total, f.pagina(), f.tamano(), totales);
    }

    @Override
    public List<Intento> intentos(String sn) {
        return jdbc.sql("""
                        SELECT e.id, e.lote_id, l.numero, coalesce(e.terminada_en, e.iniciada_en, l.creado_en) AS fecha, %s AS resultado,
                               l.simulacion, e.paso_actual, e.codigo_fallo, e.mensaje_fallo
                        FROM ejecuciones_onu e JOIN lotes l ON l.id = e.lote_id
                        WHERE e.sn = ?
                        ORDER BY fecha DESC
                        """.formatted(RESULTADO))
                .param(sn)
                .query((rs, n) -> new Intento(rs.getLong("id"), rs.getLong("lote_id"), rs.getString("numero"), instante(rs, "fecha"),
                        rs.getString("resultado"), rs.getBoolean("simulacion"), rs.getString("paso_actual"), rs.getString("codigo_fallo"),
                        rs.getString("mensaje_fallo")))
                .list();
    }

    private static Entrada entrada(ResultSet rs, int n) throws SQLException {
        int pon = rs.getInt("pon");
        Integer ponONulo = rs.wasNull() ? null : pon;
        int onu = rs.getInt("onu_id");
        Integer onuONulo = rs.wasNull() ? null : onu;
        boolean revisada = rs.getBoolean("revisada");
        Boolean revisadaONulo = rs.wasNull() ? null : revisada;
        return new Entrada(rs.getString("sn"), rs.getString("ip"), rs.getString("olt_codigo"), rs.getString("tarjeta"), ponONulo, onuONulo,
                rs.getString("resultado"), revisadaONulo, rs.getString("codigo_fallo"), rs.getString("mensaje_fallo"), rs.getLong("lote_id"),
                rs.getString("numero"), instante(rs, "fecha"));
    }

    private static Instant instante(ResultSet rs, String columna) throws SQLException {
        OffsetDateTime v = rs.getObject(columna, OffsetDateTime.class);
        return v == null ? null : v.toInstant();
    }
}
