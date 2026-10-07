package com.guajiranet.migradoronu.lotes.infraestructura.salida.persistencia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.jdbc.core.simple.JdbcClient;

import com.guajiranet.migradoronu.lotes.aplicacion.LoteDetalle;
import com.guajiranet.migradoronu.lotes.dominio.BatchFilters;
import com.guajiranet.migradoronu.lotes.dominio.BatchMode;
import com.guajiranet.migradoronu.lotes.dominio.BatchNumber;
import com.guajiranet.migradoronu.lotes.dominio.BatchStatus;
import com.guajiranet.migradoronu.lotes.dominio.ClassifiedOnu;
import com.guajiranet.migradoronu.lotes.dominio.DuplicateEntry;
import com.guajiranet.migradoronu.lotes.dominio.ItemGroup;
import com.guajiranet.migradoronu.lotes.dominio.ScheduledOnu;
import com.guajiranet.migradoronu.lotes.puertos.salida.BatchRepositoryPort;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Las tablas lotes y lote_items con consultas nativas. */
public class JdbcBatchRepositoryAdapter implements BatchRepositoryPort {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<DuplicateEntry>> DUPLICADOS = new TypeReference<>() { };
    private static final TypeReference<Map<String, Object>> MAPA = new TypeReference<>() { };

    private static final String LOTE = """
            SELECT l.*, l.contadores::text AS contadores_json,
                   (SELECT count(*) FROM lote_items i WHERE i.lote_id = l.id AND i.grupo = 'ENTRA') AS n_entran,
                   (SELECT count(*) FROM lote_items i WHERE i.lote_id = l.id AND i.grupo = 'EXCLUIDA') AS n_excluidas,
                   (SELECT count(*) FROM lote_items i WHERE i.lote_id = l.id AND i.grupo = 'YA_TIENE_PERFIL') AS n_ya_tienen
            FROM lotes l
            """;
    private static final String ITEM = """
            SELECT id, sn, ip, grupo, codigo_motivo, mensaje, cliente, contrato, marca, modelo, olt_codigo, tarjeta, pon, onu_id,
                   perfil_actual, registros_duplicados::text AS duplicados
            FROM lote_items
            """;

    private final JdbcClient jdbc;

    public JdbcBatchRepositoryAdapter(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    @Override
    public long siguienteConsecutivo() {
        return jdbc.sql("SELECT nextval('lotes_numero_seq')").query(Long.class).single();
    }

    @Override
    public long crearVistaPrevia(BatchNumber numero, BatchMode modo, BatchFilters filtros, long usuarioId, Instant ahora,
                                 Instant venceEn, Map<String, Object> parametros, List<ClassifiedOnu> items) {
        long id = jdbc.sql("""
                        INSERT INTO lotes (numero, modo, marca, filtro_olt, filtro_tarjeta, filtro_pon, estado, creado_por, creado_en,
                                           vence_en, parametros_usados)
                        VALUES (?, ?, ?, ?, ?, ?, 'VISTA_PREVIA', ?, ?, ?, CAST(? AS jsonb))
                        RETURNING id
                        """)
                .params(numero.value(), modo.name(), filtros.marca(), filtros.oltCodigo(), filtros.tarjeta(), filtros.pon(), usuarioId,
                        Timestamp.from(ahora), Timestamp.from(venceEn), JSON.writeValueAsString(parametros))
                .query(Long.class).single();
        int orden = 0;
        for (ClassifiedOnu c : items) {
            boolean conPosicion = c.tarjeta() != null;
            jdbc.sql("""
                            INSERT INTO lote_items (lote_id, sn, ip, grupo, codigo_motivo, mensaje, cliente, contrato, marca, modelo,
                                                    olt_codigo, olt_id, tarjeta, pon, onu_id, perfil_actual, registros_duplicados, orden)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, (SELECT id FROM olts WHERE codigo = ?), ?, ?, ?, ?, CAST(? AS jsonb), ?)
                            """)
                    .params(id, c.sn(), c.ip(), c.grupo().name(), c.codigoMotivo(), c.mensaje(), c.cliente(), c.contrato(), c.marca(),
                            c.modelo(), c.oltCodigo(), c.oltCodigo(), c.tarjeta(), conPosicion ? c.pon() : null,
                            conPosicion ? c.onuId() : null, c.perfilActual(), JSON.writeValueAsString(c.registrosDuplicados()), orden++)
                    .update();
        }
        return id;
    }

    @Override
    public Optional<LoteDetalle> detalle(long loteId) {
        return jdbc.sql(LOTE + " WHERE l.id = ?").param(loteId).query(JdbcBatchRepositoryAdapter::lote).optional();
    }

    @Override
    public List<LoteDetalle> recientes(int limite) {
        return jdbc.sql(LOTE + " ORDER BY l.creado_en DESC LIMIT ?").param(Math.max(1, Math.min(limite, 200)))
                .query(JdbcBatchRepositoryAdapter::lote).list();
    }

    @Override
    public List<ClassifiedOnu> items(long loteId) {
        return jdbc.sql(ITEM + " WHERE lote_id = ? ORDER BY orden").param(loteId).query(JdbcBatchRepositoryAdapter::item).list();
    }

    @Override
    public List<ScheduledOnu> itemsQueEntran(long loteId) {
        return jdbc.sql("SELECT id, sn, orden FROM lote_items WHERE lote_id = ? AND grupo = 'ENTRA' ORDER BY orden")
                .param(loteId)
                .query((rs, n) -> new ScheduledOnu(rs.getLong("id"), rs.getString("sn"), n))
                .list();
    }

    @Override
    public Optional<BatchStatus> estadoBloqueando(long loteId) {
        return jdbc.sql("SELECT estado FROM lotes WHERE id = ? FOR UPDATE").param(loteId)
                .query((rs, n) -> BatchStatus.valueOf(rs.getString(1))).optional();
    }

    @Override
    public Map<String, Object> parametrosDeVistaPrevia(long loteId) {
        String json = jdbc.sql("SELECT parametros_usados::text FROM lotes WHERE id = ?").param(loteId).query(String.class).optional().orElse(null);
        return json == null ? Map.of() : JSON.readValue(json, MAPA);
    }

    @Override
    public void confirmar(long loteId, long usuarioId, boolean simulacion, Instant ahora, Map<String, Object> parametros,
                          String procedimientoVersion) {
        jdbc.sql("""
                        UPDATE lotes SET estado = 'CONFIRMADO', simulacion = ?, confirmado_por = ?, confirmado_en = ?,
                                         parametros_usados = CAST(? AS jsonb), procedimiento_version = ?
                        WHERE id = ?
                        """)
                .params(simulacion, usuarioId, Timestamp.from(ahora), JSON.writeValueAsString(parametros), procedimientoVersion, loteId)
                .update();
    }

    @Override
    public void cambiarEstado(long loteId, BatchStatus estado, Long usuarioId, String motivo, Instant ahora) {
        boolean detencion = estado == BatchStatus.DETENIENDOSE || estado == BatchStatus.DETENIDO;
        jdbc.sql("""
                        UPDATE lotes SET estado = ?,
                               detenido_por = CASE WHEN ? THEN ? ELSE detenido_por END,
                               motivo_detencion = CASE WHEN ? THEN ? ELSE motivo_detencion END,
                               terminado_en = CASE WHEN ? THEN ? ELSE terminado_en END
                        WHERE id = ?
                        """)
                .params(estado.name(), detencion, usuarioId, detencion || estado == BatchStatus.DESCARTADO, motivo,
                        estado.isFinal(), Timestamp.from(ahora), loteId)
                .update();
    }

    private static LoteDetalle lote(ResultSet rs, int n) throws SQLException {
        Map<String, Object> guardados = JSON.readValue(rs.getString("contadores_json"), MAPA);
        Map<String, Long> contadores = new LinkedHashMap<>();
        contadores.put("entran", rs.getLong("n_entran"));
        contadores.put("excluidas", rs.getLong("n_excluidas"));
        contadores.put("yaTienenPerfil", rs.getLong("n_ya_tienen"));
        for (String clave : List.of("pendientes", "enCurso", "completadas", "fallidas", "excluidasAlEjecutar", "noIniciadas")) {
            contadores.put(clave, guardados.get(clave) instanceof Number v ? v.longValue() : 0L);
        }
        int pon = rs.getInt("filtro_pon");
        Integer filtroPon = rs.wasNull() ? null : pon;
        long confirmadoPor = rs.getLong("confirmado_por");
        Long confirmado = rs.wasNull() ? null : confirmadoPor;
        return new LoteDetalle(rs.getLong("id"), rs.getString("numero"), BatchMode.valueOf(rs.getString("modo")),
                rs.getBoolean("simulacion"), BatchStatus.valueOf(rs.getString("estado")),
                new BatchFilters(rs.getString("marca"), rs.getString("filtro_olt"), rs.getString("filtro_tarjeta"), filtroPon),
                rs.getLong("creado_por"), instante(rs, "creado_en"), instante(rs, "vence_en"), confirmado,
                instante(rs, "confirmado_en"), instante(rs, "iniciado_en"), instante(rs, "terminado_en"),
                rs.getString("motivo_detencion"), rs.getString("procedimiento_version"), contadores);
    }

    private static ClassifiedOnu item(ResultSet rs, int n) throws SQLException {
        int pon = rs.getInt("pon");
        int onuId = rs.getInt("onu_id");
        return new ClassifiedOnu(rs.getString("sn"), rs.getString("ip"), ItemGroup.valueOf(rs.getString("grupo")),
                rs.getString("codigo_motivo"), rs.getString("mensaje"), rs.getString("cliente"), rs.getString("contrato"),
                rs.getString("marca"), rs.getString("modelo"), rs.getString("olt_codigo"), rs.getString("tarjeta"), pon, onuId,
                rs.getString("perfil_actual"), JSON.readValue(rs.getString("duplicados"), DUPLICADOS));
    }

    private static Instant instante(ResultSet rs, String columna) throws SQLException {
        OffsetDateTime valor = rs.getObject(columna, OffsetDateTime.class);
        return valor == null ? null : valor.toInstant();
    }
}
