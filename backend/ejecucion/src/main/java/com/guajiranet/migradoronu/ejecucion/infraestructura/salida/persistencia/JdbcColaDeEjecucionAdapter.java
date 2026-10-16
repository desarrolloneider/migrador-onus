package com.guajiranet.migradoronu.ejecucion.infraestructura.salida.persistencia;

import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.guajiranet.migradoronu.ejecucion.aplicacion.TrabajoOnu;
import com.guajiranet.migradoronu.ejecucion.puertos.salida.ColaDeEjecucionPort;

/** La cola de trabajo sobre ejecuciones_onu, lotes y lote_items. Cada operación es una transacción. */
public class JdbcColaDeEjecucionAdapter implements ColaDeEjecucionPort {

    /** Contadores del lote recalculados desde sus ejecuciones (spec 7.1). */
    private static final String CONTADORES = """
            UPDATE lotes l SET contadores = jsonb_build_object(
                'pendientes',          (SELECT count(*) FROM ejecuciones_onu e WHERE e.lote_id = l.id AND e.estado = 'PENDIENTE'),
                'enCurso',             (SELECT count(*) FROM ejecuciones_onu e WHERE e.lote_id = l.id AND e.estado IN ('EN_CURSO', 'REVALIDANDO')),
                'completadas',         (SELECT count(*) FROM ejecuciones_onu e WHERE e.lote_id = l.id AND e.estado = 'COMPLETADA'),
                'fallidas',            (SELECT count(*) FROM ejecuciones_onu e WHERE e.lote_id = l.id AND e.estado = 'FALLIDA'),
                'excluidasAlEjecutar', (SELECT count(*) FROM ejecuciones_onu e WHERE e.lote_id = l.id AND e.estado = 'EXCLUIDA_AL_EJECUTAR'),
                'noIniciadas',         (SELECT count(*) FROM ejecuciones_onu e WHERE e.lote_id = l.id AND e.estado = 'NO_INICIADA'))
            WHERE l.id = ?
            """;

    /** Si al lote no le quedan ONUs por hacer, se cierra: detenido, terminado con fallas o terminado. */
    private static final String CERRAR_SI_TERMINO = """
            UPDATE lotes l SET terminado_en = now(), estado = CASE
                    WHEN l.estado = 'DETENIENDOSE' THEN 'DETENIDO'
                    WHEN EXISTS (SELECT 1 FROM ejecuciones_onu e WHERE e.lote_id = l.id AND e.estado = 'FALLIDA') THEN 'TERMINADO_CON_FALLAS'
                    ELSE 'TERMINADO' END
            WHERE l.id = ? AND l.estado IN ('CONFIRMADO', 'EN_EJECUCION', 'DETENIENDOSE')
              AND NOT EXISTS (SELECT 1 FROM ejecuciones_onu e WHERE e.lote_id = l.id AND e.estado IN ('PENDIENTE', 'REVALIDANDO', 'EN_CURSO'))
            """;

    private final JdbcClient jdbc;
    private final TransactionTemplate transaccion;

    public JdbcColaDeEjecucionAdapter(DataSource dataSource, PlatformTransactionManager transacciones) {
        this.jdbc = JdbcClient.create(dataSource);
        this.transaccion = new TransactionTemplate(transacciones);
    }

    @Override
    public java.util.List<String> reintentarFallidas(long loteId, java.util.List<Long> ejecuciones) {
        return transaccion.execute(estado -> {
            String estadoLote = jdbc.sql("SELECT estado FROM lotes WHERE id = ? FOR UPDATE").param(loteId).query(String.class).optional().orElse(null);
            if (estadoLote == null || !java.util.List.of("TERMINADO_CON_FALLAS", "TERMINADO", "DETENIDO", "EN_EJECUCION").contains(estadoLote)) {
                return java.util.List.<String>of();
            }
            boolean todas = ejecuciones == null || ejecuciones.isEmpty();
            String soloEstas = todas ? "" : " AND e.id IN (" + String.join(",", ejecuciones.stream().map(id -> "?").toList()) + ")";
            java.util.List<Object> parametros = new java.util.ArrayList<>();
            parametros.add(loteId);
            if (!todas) {
                parametros.addAll(ejecuciones);
            }
            // Una ONU que ya está activa en otro lote no se reabre aquí (solo puede estar en uno a la vez).
            java.util.List<String> sns = jdbc.sql("""
                    UPDATE ejecuciones_onu e SET estado = 'PENDIENTE', paso_actual = NULL, codigo_fallo = NULL, mensaje_fallo = NULL,
                           estado_al_fallar = NULL, iniciada_en = NULL, terminada_en = NULL, proceso_id = NULL,
                           revisada = false, revisada_por = NULL, revisada_en = NULL, nota_revision = NULL
                    WHERE e.lote_id = ? AND e.estado = 'FALLIDA'""" + soloEstas + """
                      AND NOT EXISTS (SELECT 1 FROM ejecuciones_onu o WHERE o.sn = e.sn AND o.id <> e.id
                                      AND o.estado IN ('PENDIENTE', 'REVALIDANDO', 'EN_CURSO'))
                    RETURNING e.sn
                    """).params(parametros).query(String.class).list();
            if (!sns.isEmpty()) {
                jdbc.sql("UPDATE lotes SET estado = 'EN_EJECUCION', terminado_en = NULL WHERE id = ?").param(loteId).update();
                jdbc.sql(CONTADORES).param(loteId).update();
            }
            return sns;
        });
    }

    @Override
    public void atenderDetenciones() {
        transaccion.executeWithoutResult(estado -> {
            var lotes = jdbc.sql("SELECT id FROM lotes WHERE estado = 'DETENIENDOSE' FOR UPDATE").query(Long.class).list();
            for (long loteId : lotes) {
                jdbc.sql("UPDATE ejecuciones_onu SET estado = 'NO_INICIADA', terminada_en = now() WHERE lote_id = ? AND estado = 'PENDIENTE'")
                        .param(loteId).update();
                jdbc.sql(CONTADORES).param(loteId).update();
                jdbc.sql(CERRAR_SI_TERMINO).param(loteId).update();
            }
        });
    }

    @Override
    public java.util.List<String> oltsConPendientes() {
        return jdbc.sql("""
                        SELECT i.olt_codigo FROM ejecuciones_onu e JOIN lotes l ON l.id = e.lote_id JOIN lote_items i ON i.id = e.lote_item_id
                        WHERE e.estado = 'PENDIENTE' AND l.estado IN ('CONFIRMADO', 'EN_EJECUCION')
                        GROUP BY i.olt_codigo
                        ORDER BY min(l.confirmado_en), min(e.orden)
                        """)
                .query(String.class).list();
    }

    @Override
    public Optional<TrabajoOnu> tomarSiguiente(long procesoId, String oltCodigo) {
        return reintentando(() -> transaccion.execute(estado -> {
            Optional<Long> id = jdbc.sql("""
                            SELECT e.id FROM ejecuciones_onu e JOIN lotes l ON l.id = e.lote_id JOIN lote_items i ON i.id = e.lote_item_id
                            WHERE e.estado = 'PENDIENTE' AND l.estado IN ('CONFIRMADO', 'EN_EJECUCION') AND i.olt_codigo = ?
                            ORDER BY l.confirmado_en, e.orden
                            LIMIT 1
                            FOR UPDATE OF e SKIP LOCKED
                            """)
                    .param(oltCodigo).query(Long.class).optional();
            if (id.isEmpty()) {
                return Optional.<TrabajoOnu>empty();
            }
            jdbc.sql("UPDATE ejecuciones_onu SET estado = 'EN_CURSO', paso_actual = 'P01', iniciada_en = now(), proceso_id = ? WHERE id = ?")
                    .params(procesoId, id.get()).update();
            TrabajoOnu trabajo = jdbc.sql("""
                            SELECT e.id, e.lote_id, l.numero, l.simulacion, l.confirmado_por, e.sn, i.olt_codigo, i.tarjeta, i.pon, i.onu_id, l.tipo,
                                   l.parametros_usados->>'perfil_tr069' AS perfil,
                                   l.parametros_usados->>'guardar_configuracion_olt' AS guardar,
                                   (SELECT string_agg(v, ',') FROM jsonb_array_elements_text(
                                       CASE WHEN jsonb_typeof(l.parametros_usados->'_perfiles_origen') = 'array'
                                            THEN l.parametros_usados->'_perfiles_origen' ELSE '[]'::jsonb END) v) AS origenes
                            FROM ejecuciones_onu e JOIN lotes l ON l.id = e.lote_id JOIN lote_items i ON i.id = e.lote_item_id
                            WHERE e.id = ?
                            """)
                    .param(id.get())
                    .query((rs, n) -> new TrabajoOnu(rs.getLong("id"), rs.getLong("lote_id"), rs.getString("numero"), rs.getBoolean("simulacion"),
                            rs.getLong("confirmado_por"), rs.getString("sn"), rs.getString("olt_codigo"), rs.getString("tarjeta"),
                            rs.getInt("pon"), rs.getInt("onu_id"), rs.getString("perfil") == null ? "GUAJIRANET1" : rs.getString("perfil"),
                            rs.getString("guardar") != null && !"NO_GUARDAR".equals(rs.getString("guardar")),
                            rs.getString("origenes") == null ? null : java.util.List.of(rs.getString("origenes").split(",")),
                            rs.getString("tipo")))
                    .single();
            jdbc.sql("UPDATE lotes SET estado = 'EN_EJECUCION', iniciado_en = coalesce(iniciado_en, now()) WHERE id = ? AND estado = 'CONFIRMADO'")
                    .param(trabajo.loteId()).update();
            jdbc.sql(CONTADORES).param(trabajo.loteId()).update();
            return Optional.of(trabajo);
        }));
    }

    /**
     * Varias OLT terminan ONUs del mismo lote a la vez y todas recalculan sus contadores: si la base corta una
     * transacción por bloqueo cruzado, se repite. Nunca se marca como fallida una ONU por esto.
     */
    private static <T> T reintentando(java.util.function.Supplier<T> operacion) {
        for (int intento = 1; ; intento++) {
            try {
                return operacion.get();
            } catch (org.springframework.dao.PessimisticLockingFailureException e) {
                if (intento >= 5) {
                    throw e;
                }
                try {
                    Thread.sleep(100L * intento);
                } catch (InterruptedException i) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    @Override
    public void terminar(long ejecucionId, String estado, String paso, String codigoFallo, String mensaje) {
        reintentando(() -> transaccion.execute(s -> {
            long loteId = jdbc.sql("SELECT lote_id FROM ejecuciones_onu WHERE id = ?").param(ejecucionId).query(Long.class).single();
            jdbc.sql("""
                            UPDATE ejecuciones_onu SET estado = ?, paso_actual = ?, codigo_fallo = ?, mensaje_fallo = ?, terminada_en = now()
                            WHERE id = ?
                            """)
                    .params(estado, paso == null ? "P01" : paso, codigoFallo,
                            "COMPLETADA".equals(estado) ? null : mensaje, ejecucionId)
                    .update();
            jdbc.sql(CONTADORES).param(loteId).update();
            jdbc.sql(CERRAR_SI_TERMINO).param(loteId).update();
            // Si la ONU quedó con el perfil nuevo en un lote real, sus fallas anteriores quedan resueltas.
            jdbc.sql(RESOLVER_FALLIDAS_ANTERIORES).params(ejecucionId).update();
            return null;
        }));
    }

    /**
     * Las fallidas anteriores de la misma ONU pasan a resueltas (y revisadas) cuando esta ejecución terminó bien en un
     * lote real: completada, o excluida sin código porque ya tenía el perfil. No se cuentan ni se ofrecen para reintentar.
     */
    private static final String RESOLVER_FALLIDAS_ANTERIORES = """
            UPDATE ejecuciones_onu f
            SET revisada = TRUE,
                revisada_por = coalesce(f.revisada_por, l.confirmado_por),
                revisada_en = coalesce(f.revisada_en, now()),
                nota_revision = coalesce(f.nota_revision, 'Resuelta: la ONU se cambió después en el lote ' || l.numero || '.'),
                resuelta_en_lote_id = e.lote_id
            FROM ejecuciones_onu e JOIN lotes l ON l.id = e.lote_id
            WHERE e.id = ? AND NOT l.simulacion
              AND (e.estado = 'COMPLETADA' OR (e.estado = 'EXCLUIDA_AL_EJECUTAR' AND e.codigo_fallo IS NULL))
              AND f.sn = e.sn AND f.id <> e.id AND f.estado = 'FALLIDA' AND f.resuelta_en_lote_id IS NULL
            """;

    @Override
    public int recuperarInterrumpidas() {
        return transaccion.execute(s -> {
            var lotes = jdbc.sql("SELECT DISTINCT lote_id FROM ejecuciones_onu WHERE estado IN ('EN_CURSO', 'REVALIDANDO')").query(Long.class).list();
            int n = jdbc.sql("""
                            UPDATE ejecuciones_onu SET estado = 'FALLIDA', codigo_fallo = 'FALLO_INTERRUMPIDA',
                                   paso_actual = coalesce(paso_actual, 'P01'), terminada_en = now(),
                                   mensaje_fallo = 'El ejecutor se detuvo mientras esta ONU estaba en curso. Revisa en la OLT cómo quedó su perfil.'
                            WHERE estado IN ('EN_CURSO', 'REVALIDANDO')
                            """).update();
            for (long loteId : lotes) {
                jdbc.sql(CONTADORES).param(loteId).update();
                jdbc.sql(CERRAR_SI_TERMINO).param(loteId).update();
            }
            return n;
        });
    }

    @Override
    public void avanzarPaso(long ejecucionId, String paso) {
        jdbc.sql("UPDATE ejecuciones_onu SET paso_actual = ? WHERE id = ? AND estado IN ('EN_CURSO', 'REVALIDANDO')")
                .params(paso, ejecucionId).update();
    }

    @Override
    public long registrarProceso(String host, long pid) {
        return jdbc.sql("INSERT INTO procesos_ejecutores (host, pid) VALUES (?, ?) RETURNING id").params(host, pid).query(Long.class).single();
    }

    @Override
    public void latido(long procesoId) {
        jdbc.sql("UPDATE procesos_ejecutores SET ultimo_latido_en = now() WHERE id = ?").param(procesoId).update();
    }

    @Override
    public void detenerProceso(long procesoId) {
        jdbc.sql("UPDATE procesos_ejecutores SET detenido_en = now() WHERE id = ?").param(procesoId).update();
    }
}
