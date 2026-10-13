package com.guajiranet.migradoronu.ensamblaje.lotes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.guajiranet.migradoronu.conciliacion.aplicacion.ClassifyOnusCommand;
import com.guajiranet.migradoronu.conciliacion.dominio.Classification;
import com.guajiranet.migradoronu.conciliacion.dominio.DuplicateRecord;
import com.guajiranet.migradoronu.conciliacion.dominio.ExclusionReason;
import com.guajiranet.migradoronu.conciliacion.dominio.OnuCandidate;
import com.guajiranet.migradoronu.conciliacion.dominio.OnuPosition;
import com.guajiranet.migradoronu.conciliacion.dominio.Subscriber;
import com.guajiranet.migradoronu.conciliacion.puertos.entrada.ClassifyOnusPort;
import com.guajiranet.migradoronu.lotes.dominio.BatchFilters;
import com.guajiranet.migradoronu.lotes.dominio.ClassifiedOnu;
import com.guajiranet.migradoronu.lotes.dominio.DuplicateEntry;
import com.guajiranet.migradoronu.lotes.dominio.ItemGroup;

class OnuClassifierModuleAdapterTest {

    private static final OnuPosition POS = new OnuPosition("RIOHACHA01", "0/1", 0, 33);
    private static final OnuCandidate ONU = new OnuCandidate("HWTC1A2B3C4D", "HUAWEI", "HG8145V5", POS, true, "10.20.30.40", "acs_viejo");

    private final AtomicReference<ClassifyOnusCommand> received = new AtomicReference<>();

    @Test
    void traduceLosFiltrosYCadaClasificacionAlModeloDeLotes() {
        Subscriber subscriber = new Subscriber("Ana Pérez", "C-1001", "10.20.30.40", "ACTIVO");
        DuplicateRecord duplicate = new DuplicateRecord(new OnuPosition("MAICAO02", "0/0", 3, 7), false);
        OnuClassifierModuleAdapter adapter = new OnuClassifierModuleAdapter(classifierReturning(
                Classification.included("HWTC1A2B3C4D", ONU, subscriber, List.of(duplicate))));

        List<ClassifiedOnu> result = adapter.classify(new BatchFilters("HUAWEI", "RIOHACHA01", "0/1", null));

        assertThat(received.get()).isEqualTo(new ClassifyOnusCommand("HUAWEI", "RIOHACHA01", "0/1", null, null, null, "PERFIL_TR069"));
        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.grupo()).isEqualTo(ItemGroup.ENTRA);
            assertThat(item.codigoMotivo()).isNull();
            assertThat(item.contrato()).isEqualTo("C-1001");
            assertThat(item.tarjeta()).isEqualTo("0/1");
            assertThat(item.onuId()).isEqualTo(33);
            assertThat(item.perfilActual()).isEqualTo("acs_viejo");
            assertThat(item.registrosDuplicados()).containsExactly(new DuplicateEntry("MAICAO02", "0/0", 3, 7, false));
        });
    }

    @Test
    void unaExcluidaLlegaConSuCodigoYMensajeAunqueNoTengaContrato() {
        String message = "ONU HWTC1A2B3C4D no entra al lote: su IP 10.20.30.40 (según SmartOLT) no está en ningún contrato de Wispro.";
        OnuClassifierModuleAdapter adapter = new OnuClassifierModuleAdapter(classifierReturning(
                Classification.excluded("HWTC1A2B3C4D", ExclusionReason.EXC_IP_NO_EN_WISPRO, message, ONU, null, null)));

        ClassifiedOnu item = adapter.classify(new BatchFilters("HUAWEI", null, null, null)).getFirst();

        assertThat(item.grupo()).isEqualTo(ItemGroup.EXCLUIDA);
        assertThat(item.codigoMotivo()).isEqualTo("EXC_IP_NO_EN_WISPRO");
        assertThat(item.mensaje()).isEqualTo(message);
        assertThat(item.cliente()).isNull();
    }

    private ClassifyOnusPort classifierReturning(Classification classification) {
        return new ClassifyOnusPort() {
            @Override
            public List<Classification> classify(ClassifyOnusCommand command) {
                received.set(command);
                return List.of(classification);
            }

            @Override
            public Optional<Classification> classifySingle(String sn) {
                return Optional.of(classification);
            }
        };
    }
}
