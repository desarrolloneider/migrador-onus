package com.guajiranet.migradoronu.ensamblaje.lotes;

import java.util.List;

import org.springframework.stereotype.Component;

import com.guajiranet.migradoronu.ejecucion.aplicacion.ScheduleOnuRunsCommand;
import com.guajiranet.migradoronu.ejecucion.puertos.entrada.ScheduleOnuRunsPort;
import com.guajiranet.migradoronu.lotes.dominio.ScheduledOnu;
import com.guajiranet.migradoronu.lotes.puertos.salida.RunSchedulerPort;
import com.guajiranet.migradoronu.procedimientos.dominio.ProcedureVersion;

/**
 * Al confirmar, lotes crea las ejecuciones en ejecucion. Corre en el mismo hilo y la misma transacción de la
 * confirmación: si falla, no queda un lote confirmado sin ejecuciones (arquitectura 7).
 */
@Component
public class RunSchedulerModuleAdapter implements RunSchedulerPort {

    private final ScheduleOnuRunsPort scheduler;

    public RunSchedulerModuleAdapter(ScheduleOnuRunsPort scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public void schedule(long loteId, String procedimientoVersion, List<ScheduledOnu> onus) {
        List<ScheduleOnuRunsCommand.Item> items = onus.stream()
                .map(onu -> new ScheduleOnuRunsCommand.Item(onu.loteItemId(), onu.sn(), onu.orden()))
                .toList();
        scheduler.schedule(new ScheduleOnuRunsCommand(loteId, new ProcedureVersion(procedimientoVersion), items));
    }
}
