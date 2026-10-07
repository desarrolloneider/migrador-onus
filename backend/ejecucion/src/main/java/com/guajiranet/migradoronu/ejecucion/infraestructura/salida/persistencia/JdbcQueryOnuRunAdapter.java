package com.guajiranet.migradoronu.ejecucion.infraestructura.salida.persistencia;

import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.jdbc.core.simple.JdbcClient;

import com.guajiranet.migradoronu.ejecucion.aplicacion.ActiveRunView;
import com.guajiranet.migradoronu.ejecucion.aplicacion.UnreviewedFailureView;
import com.guajiranet.migradoronu.ejecucion.dominio.FailureCode;
import com.guajiranet.migradoronu.ejecucion.dominio.OnuRunStatus;
import com.guajiranet.migradoronu.ejecucion.puertos.entrada.QueryOnuRunPort;
import com.guajiranet.migradoronu.procedimientos.dominio.StepCode;

/** Lecturas de ejecuciones_onu para la clasificación (RN-16, RN-26). */
public class JdbcQueryOnuRunAdapter implements QueryOnuRunPort {

    private final JdbcClient jdbc;

    public JdbcQueryOnuRunAdapter(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    @Override
    public Optional<ActiveRunView> findActiveRun(String sn) {
        return jdbc.sql("""
                        SELECT id, lote_id, estado FROM ejecuciones_onu
                        WHERE sn = ? AND estado IN ('PENDIENTE', 'REVALIDANDO', 'EN_CURSO')
                        """)
                .param(sn)
                .query((rs, n) -> new ActiveRunView(rs.getLong("id"), rs.getLong("lote_id"), OnuRunStatus.valueOf(rs.getString("estado"))))
                .optional();
    }

    @Override
    public Optional<UnreviewedFailureView> findUnreviewedFailure(String sn) {
        return jdbc.sql("""
                        SELECT id, lote_id, paso_actual, codigo_fallo FROM ejecuciones_onu
                        WHERE sn = ? AND estado = 'FALLIDA' AND NOT revisada
                        ORDER BY terminada_en DESC NULLS LAST LIMIT 1
                        """)
                .param(sn)
                .query((rs, n) -> new UnreviewedFailureView(rs.getLong("id"), rs.getLong("lote_id"),
                        StepCode.valueOf(rs.getString("paso_actual")), FailureCode.valueOf(rs.getString("codigo_fallo"))))
                .optional();
    }
}
