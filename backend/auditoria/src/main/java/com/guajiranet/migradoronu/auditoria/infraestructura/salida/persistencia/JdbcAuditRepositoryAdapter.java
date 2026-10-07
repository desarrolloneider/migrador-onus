package com.guajiranet.migradoronu.auditoria.infraestructura.salida.persistencia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import javax.sql.DataSource;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import com.guajiranet.migradoronu.auditoria.aplicacion.AuditPage;
import com.guajiranet.migradoronu.auditoria.aplicacion.AuditSearchCriteria;
import com.guajiranet.migradoronu.auditoria.dominio.AuditEvent;
import com.guajiranet.migradoronu.auditoria.dominio.CanonicalJson;
import com.guajiranet.migradoronu.auditoria.dominio.ChainHead;
import com.guajiranet.migradoronu.auditoria.dominio.SealedAuditEvent;
import com.guajiranet.migradoronu.auditoria.dominio.StoredAuditEvent;
import com.guajiranet.migradoronu.auditoria.puertos.salida.AuditRepositoryPort;
import com.guajiranet.migradoronu.comun.puertos.ActorType;
import com.guajiranet.migradoronu.comun.puertos.AuditAction;
import com.guajiranet.migradoronu.comun.puertos.AuditUnavailableException;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * auditoria_eventos con consultas nativas (arquitectura 7.1 y 10.2). Solo hay INSERT y SELECT: no existe ningún
 * camino en el código para cambiar o borrar un evento.
 *
 * <p>Cada inserción va en su propia transacción ({@code REQUIRES_NEW}) y se serializa con
 * {@code pg_advisory_xact_lock}: así la huella anterior es siempre la del último evento confirmado, el evento queda
 * confirmado antes de volver (RN-11) y el bloqueo dura solo lo que tarda la inserción.
 */
public class JdbcAuditRepositoryAdapter implements AuditRepositoryPort {

    /** Clave del bloqueo consultivo de la cadena. Las claves de la herramienta no deben repetirse entre sí. */
    static final long CHAIN_LOCK_KEY = 0x4D49_4752_4155_4401L;

    private static final String COLUMNS = """
            SELECT id, ocurrido_en, actor_tipo, usuario_id, ip_origen, accion, lote_id, ejecucion_id, sn, paso, sistema,
                   peticion::text AS peticion, respuesta::text AS respuesta, resultado, codigo, mensaje, duracion_ms,
                   hash_anterior, hash
            FROM auditoria_eventos
            """;

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final JdbcClient jdbc;
    private final TransactionTemplate ownTransaction;
    private final JsonMapper json = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    public JdbcAuditRepositoryAdapter(DataSource dataSource, PlatformTransactionManager transactionManager) {
        this.jdbc = JdbcClient.create(dataSource);
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public StoredAuditEvent append(Function<String, SealedAuditEvent> sealer) {
        try {
            return ownTransaction.execute(status -> {
                jdbc.sql("SELECT pg_advisory_xact_lock(:key)").param("key", CHAIN_LOCK_KEY).query().listOfRows();
                String previousHash = jdbc.sql("SELECT hash FROM auditoria_eventos ORDER BY id DESC LIMIT 1")
                        .query(String.class).optional().orElse(null);
                SealedAuditEvent sealed = sealer.apply(previousHash);
                if (!Objects.equals(sealed.previousHash(), previousHash)) {
                    throw new IllegalStateException("El evento se selló con una huella anterior que no es la última");
                }
                long id = insert(sealed);
                return StoredAuditEvent.readable(id, sealed.previousHash(), sealed.hash(), sealed.event());
            });
        } catch (DataAccessException | TransactionException e) {
            throw new AuditUnavailableException(
                    "No se pudo confirmar el evento en la auditoría: la base de datos no respondió. No se actúa (RN-30).", e);
        }
    }

    @Override
    public Optional<ChainHead> head() {
        return jdbc.sql("SELECT id, hash FROM auditoria_eventos ORDER BY id DESC LIMIT 1")
                .query((rs, n) -> new ChainHead(rs.getLong("id"), rs.getString("hash")))
                .optional();
    }

    @Override
    public List<StoredAuditEvent> readChain(long afterId, long upToId, int limit) {
        return jdbc.sql(COLUMNS + " WHERE id > :after AND id <= :upTo ORDER BY id LIMIT :limit")
                .param("after", afterId)
                .param("upTo", upToId)
                .param("limit", limit)
                .query(this::toStored)
                .list();
    }

    @Override
    public AuditPage search(AuditSearchCriteria criteria) {
        StringBuilder where = new StringBuilder(" WHERE TRUE");
        Map<String, Object> params = new HashMap<>();
        filter(where, params, "ocurrido_en >= :desde", "desde", criteria.desde() == null ? null : utc(criteria.desde()));
        filter(where, params, "ocurrido_en <= :hasta", "hasta", criteria.hasta() == null ? null : utc(criteria.hasta()));
        filter(where, params, "usuario_id = :usuarioId", "usuarioId", criteria.usuarioId());
        filter(where, params, "lote_id = :loteId", "loteId", criteria.loteId());
        filter(where, params, "sn = :sn", "sn", criteria.sn());
        filter(where, params, "sistema = :sistema", "sistema", criteria.sistema());
        filter(where, params, "paso = :paso", "paso", criteria.paso());
        filter(where, params, "resultado = :resultado", "resultado", criteria.resultado());
        filter(where, params, "accion = :accion", "accion", criteria.accion() == null ? null : criteria.accion().name());

        long total = jdbc.sql("SELECT count(*) FROM auditoria_eventos" + where).params(params).query(Long.class).single();
        params.put("limit", criteria.size());
        params.put("offset", (long) criteria.page() * criteria.size());
        List<StoredAuditEvent> events = jdbc.sql(COLUMNS + where + " ORDER BY ocurrido_en, id LIMIT :limit OFFSET :offset")
                .params(params)
                .query(this::toStored)
                .list();
        return new AuditPage(events, total, criteria.page(), criteria.size());
    }

    private long insert(SealedAuditEvent sealed) {
        AuditEvent e = sealed.event();
        return jdbc.sql("""
                        INSERT INTO auditoria_eventos (ocurrido_en, actor_tipo, usuario_id, ip_origen, accion, lote_id,
                               ejecucion_id, sn, paso, sistema, peticion, respuesta, resultado, codigo, mensaje,
                               duracion_ms, hash_anterior, hash)
                        VALUES (:ocurridoEn, :actorTipo, :usuarioId, :ipOrigen, :accion, :loteId, :ejecucionId, :sn,
                                :paso, :sistema, CAST(:peticion AS jsonb), CAST(:respuesta AS jsonb), :resultado, :codigo,
                                :mensaje, :duracionMs, :hashAnterior, :hash)
                        RETURNING id
                        """)
                .param("ocurridoEn", utc(e.ocurridoEn()), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("actorTipo", e.actorTipo().name(), Types.VARCHAR)
                .param("usuarioId", e.usuarioId(), Types.BIGINT)
                .param("ipOrigen", e.ipOrigen(), Types.VARCHAR)
                .param("accion", e.accion().name(), Types.VARCHAR)
                .param("loteId", e.loteId(), Types.BIGINT)
                .param("ejecucionId", e.ejecucionId(), Types.BIGINT)
                .param("sn", e.sn(), Types.VARCHAR)
                .param("paso", e.paso(), Types.VARCHAR)
                .param("sistema", e.sistema(), Types.VARCHAR)
                .param("peticion", CanonicalJson.write(e.peticion()), Types.VARCHAR)
                .param("respuesta", CanonicalJson.write(e.respuesta()), Types.VARCHAR)
                .param("resultado", e.resultado(), Types.VARCHAR)
                .param("codigo", e.codigo(), Types.VARCHAR)
                .param("mensaje", e.mensaje(), Types.VARCHAR)
                .param("duracionMs", e.duracionMs(), Types.BIGINT)
                .param("hashAnterior", sealed.previousHash(), Types.CHAR)
                .param("hash", sealed.hash(), Types.CHAR)
                .query(Long.class)
                .single();
    }

    private StoredAuditEvent toStored(ResultSet rs, int rowNum) throws SQLException {
        long id = rs.getLong("id");
        String previousHash = rs.getString("hash_anterior");
        String hash = rs.getString("hash");
        try {
            AuditEvent event = new AuditEvent(
                    rs.getObject("ocurrido_en", OffsetDateTime.class).toInstant(),
                    ActorType.valueOf(rs.getString("actor_tipo")),
                    rs.getObject("usuario_id", Long.class),
                    rs.getString("ip_origen"),
                    AuditAction.valueOf(rs.getString("accion")),
                    rs.getObject("lote_id", Long.class),
                    rs.getObject("ejecucion_id", Long.class),
                    rs.getString("sn"),
                    rs.getString("paso"),
                    rs.getString("sistema"),
                    parse(rs.getString("peticion")),
                    parse(rs.getString("respuesta")),
                    rs.getString("resultado"),
                    rs.getString("codigo"),
                    rs.getString("mensaje"),
                    rs.getObject("duracion_ms", Long.class));
            return StoredAuditEvent.readable(id, previousHash, hash, event);
        } catch (IllegalArgumentException | JacksonException e) {
            return StoredAuditEvent.unreadable(id, previousHash, hash, e.getMessage());
        }
    }

    private Map<String, Object> parse(String jsonText) {
        return jsonText == null ? Map.of() : json.readValue(jsonText, MAP);
    }

    private static void filter(StringBuilder where, Map<String, Object> params, String clause, String name, Object value) {
        if (value != null) {
            where.append(" AND ").append(clause);
            params.put(name, value);
        }
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
