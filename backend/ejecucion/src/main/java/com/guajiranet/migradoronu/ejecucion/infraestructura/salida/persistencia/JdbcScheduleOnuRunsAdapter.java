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
}
