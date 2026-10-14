package com.guajiranet.migradoronu.ensamblaje.conciliacion;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.guajiranet.migradoronu.conciliacion.dominio.UnreviewedFailure;
import com.guajiranet.migradoronu.conciliacion.puertos.salida.OnuParticipationQueryPort;
import com.guajiranet.migradoronu.ejecucion.puertos.entrada.QueryOnuRunPort;
import com.guajiranet.migradoronu.lotes.aplicacion.BatchView;
import com.guajiranet.migradoronu.lotes.puertos.entrada.QueryBatchPort;

/**
 * K10 y K11: pregunta a ejecucion si la ONU está en un lote activo o tiene una fallida sin revisar, y a lotes el número
 * del lote para el mensaje. Combinar dos módulos para responder a un tercero solo es posible aquí (corrección 2a).
 */
@Component
public class OnuParticipationModuleAdapter implements OnuParticipationQueryPort {

    private final QueryOnuRunPort onuRuns;
    private final QueryBatchPort batches;

    private final org.springframework.jdbc.core.simple.JdbcClient jdbc;

    public OnuParticipationModuleAdapter(QueryOnuRunPort onuRuns, QueryBatchPort batches, javax.sql.DataSource dataSource) {
        this.onuRuns = onuRuns;
        this.batches = batches;
        this.jdbc = org.springframework.jdbc.core.simple.JdbcClient.create(dataSource);
    }

    @Override
    public java.util.Map<String, String> servicioWanTerminado() {
        java.util.Map<String, String> r = new java.util.HashMap<>();
        jdbc.sql("""
                SELECT DISTINCT ON (e.sn) e.sn, l.numero FROM ejecuciones_onu e JOIN lotes l ON l.id = e.lote_id
                WHERE l.tipo = 'SERVICIO_WAN' AND NOT l.simulacion AND e.estado = 'COMPLETADA'
                ORDER BY e.sn, e.terminada_en DESC
                """).query(rs -> {
                    r.put(rs.getString("sn").toUpperCase(java.util.Locale.ROOT), rs.getString("numero"));
                });
        return r;
    }

    @Override
    public Optional<String> activeBatchNumberOf(String sn) {
        return onuRuns.findActiveRun(sn).map(run -> batchNumber(run.loteId()));
    }

    @Override
    public Optional<UnreviewedFailure> unreviewedFailureOf(String sn) {
        return onuRuns.findUnreviewedFailure(sn)
                .map(failure -> new UnreviewedFailure(batchNumber(failure.loteId()), failure.paso().name()));
    }

    private String batchNumber(long loteId) {
        return batches.findById(loteId)
                .map(BatchView::numero)
                .orElseThrow(() -> new IllegalStateException("Una ejecución apunta al lote " + loteId + ", que no existe"));
    }
}
