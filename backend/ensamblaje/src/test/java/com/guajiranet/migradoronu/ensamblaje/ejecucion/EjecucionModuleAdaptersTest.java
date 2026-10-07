package com.guajiranet.migradoronu.ensamblaje.ejecucion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.guajiranet.migradoronu.conciliacion.aplicacion.RevalidateOnuCommand;
import com.guajiranet.migradoronu.conciliacion.aplicacion.RevalidationOutcome;
import com.guajiranet.migradoronu.conciliacion.dominio.OnuPosition;
import com.guajiranet.migradoronu.ejecucion.dominio.FailureCode;
import com.guajiranet.migradoronu.ejecucion.dominio.OnuRunStatus;
import com.guajiranet.migradoronu.lotes.aplicacion.OnuFinishedNotice;
import com.guajiranet.migradoronu.lotes.dominio.OnuOutcome;
import com.guajiranet.migradoronu.procedimientos.dominio.BoardRef;
import com.guajiranet.migradoronu.procedimientos.dominio.OnuTarget;
import com.guajiranet.migradoronu.procedimientos.dominio.RevalidationResult;

class EjecucionModuleAdaptersTest {

    @Test
    void laRevalidacionTraduceLaPosicionYElResultadoEntreModulos() {
        AtomicReference<RevalidateOnuCommand> received = new AtomicReference<>();
        OnuRevalidationModuleAdapter adapter = new OnuRevalidationModuleAdapter(command -> {
            received.set(command);
            return RevalidationOutcome.changed("ip", "10.20.30.40", "10.20.30.99");
        });
        OnuTarget target = new OnuTarget("HWTC1A2B3C4D", "10.20.30.40", "C-1001", "RIOHACHA01", new BoardRef(0, 1),
                new com.guajiranet.migradoronu.procedimientos.dominio.OnuPosition(0, 33));

        RevalidationResult result = adapter.revalidate(target);

        assertThat(received.get()).isEqualTo(new RevalidateOnuCommand("HWTC1A2B3C4D", "10.20.30.40", "C-1001",
                new OnuPosition("RIOHACHA01", "0/1", 0, 33)));
        assertThat(result).isEqualTo(RevalidationResult.changed("ip", "10.20.30.40", "10.20.30.99"));
    }

    @Test
    void elAvisoDeOnuTerminadaLlegaALotesConSuResultado() {
        AtomicReference<OnuFinishedNotice> received = new AtomicReference<>();
        BatchProgressModuleAdapter adapter = new BatchProgressModuleAdapter(received::set);

        adapter.onuFinished(45, "HWTC1A2B3C4D", OnuRunStatus.FALLIDA, FailureCode.FALLO_SIN_PERFIL);

        assertThat(received.get()).isEqualTo(new OnuFinishedNotice(45, "HWTC1A2B3C4D", OnuOutcome.FALLIDA, "FALLO_SIN_PERFIL"));
    }

    @Test
    void unaOnuQueNoHaTerminadoNoSeAvisa() {
        BatchProgressModuleAdapter adapter = new BatchProgressModuleAdapter(notice -> { });

        assertThatThrownBy(() -> adapter.onuFinished(45, "HWTC1A2B3C4D", OnuRunStatus.EN_CURSO, null))
                .hasMessageContaining("no ha terminado");
    }
}
