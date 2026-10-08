package com.guajiranet.migradoronu.ejecucion.infraestructura.salida.persistencia;

import javax.sql.DataSource;

import org.springframework.jdbc.core.simple.JdbcClient;

import com.guajiranet.migradoronu.ejecucion.aplicacion.ScheduleOnuRunsCommand;
import com.guajiranet.migradoronu.ejecucion.puertos.entrada.ScheduleOnuRunsPort;

/**
 * Crea una ejecución PENDIENTE por ONU del lote confirmado. Corre dentro de la transacción de la confirmación; si una
 * ONU ya está en otro lote activo, el índice único uk_ejecuciones_onu_sn_activa hace fallar todo (RN-16).
 */
public class JdbcScheduleOnuRunsAdapter implements ScheduleOnuRunsPort {

    private final JdbcClient jdbc;

    public JdbcScheduleOnuRunsAdapter(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    @Override
    public void schedule(ScheduleOnuRunsCommand command) {
        for (ScheduleOnuRunsCommand.Item item : command.items()) {
            jdbc.sql("""
                            INSERT INTO ejecuciones_onu (lote_id, lote_item_id, sn, orden, estado, procedimiento_version)
                            VALUES (?, ?, ?, ?, 'PENDIENTE', ?)
                            """)
                    .params(command.loteId(), item.loteItemId(), item.sn(), item.orden(), command.procedimientoVersion().value())
                    .update();
        }
        jdbc.sql("UPDATE lotes SET contadores = jsonb_set(contadores, '{pendientes}', to_jsonb(?::int)) WHERE id = ?")
                .params(command.items().size(), command.loteId())
                .update();
    }

    @Override
    public void resolverFallidas(long loteId, long usuarioId, java.util.List<String> sns) {
        for (String sn : sns) {
            jdbc.sql("""
                            UPDATE ejecuciones_onu f
                            SET revisada = TRUE, revisada_por = coalesce(f.revisada_por, ?), revisada_en = coalesce(f.revisada_en, now()),
                                nota_revision = coalesce(f.nota_revision, 'Resuelta: la OLT ya muestra el perfil nuevo (vista previa '
                                    || (SELECT numero FROM lotes WHERE id = ?) || ').'),
                                resuelta_en_lote_id = ?
                            WHERE f.sn = ? AND f.estado = 'FALLIDA' AND f.resuelta_en_lote_id IS NULL
                            """)
                    .params(usuarioId, loteId, loteId, sn)
                    .update();
        }
    }
}
