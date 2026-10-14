package com.guajiranet.migradoronu.conciliacion.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.guajiranet.migradoronu.conciliacion.dominio.OnuCandidate;
import com.guajiranet.migradoronu.conciliacion.dominio.OnuPosition;
import com.guajiranet.migradoronu.conciliacion.puertos.salida.OltProfilesQueryPort;
import com.guajiranet.migradoronu.conciliacion.puertos.salida.OnuInventoryPort;

class ConsultarOnusDeOltTest {

    @Test
    void marcaLasQueYaTerminaronLosTresPasos() {
        List<OnuCandidate> onus = List.of(
                new OnuCandidate("HWTC3BDD79AE", "Huawei", "EG8041V5", new OnuPosition("OLT_FONSECA", "0/1", 0, 1), true, null, null),
                new OnuCandidate("HWTC3DE624AE", "Huawei", "EG8041V5", new OnuPosition("OLT_FONSECA", "0/1", 0, 12), true, null, null));
        OnuInventoryPort smartOlt = new OnuInventoryPort() {
            public List<OnuCandidate> listOnus(String marca, String olt, String tarjeta, Integer pon) {
                return onus;
            }

            public List<OnuCandidate> findBySn(String sn) {
                return List.of();
            }
        };
        OltProfilesQueryPort perfiles = olt -> Optional.of(Map.of());
        var r = new ConsultarOnusDeOlt(smartOlt, perfiles, () -> Set.of("485754433BDD79AE")).consultar("OLT_FONSECA");
        assertThat(r.onus()).extracting(ConsultarOnusDeOlt.OnuDeOlt::sn, ConsultarOnusDeOlt.OnuDeOlt::tresPasosListos)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("485754433BDD79AE", true), org.assertj.core.groups.Tuple.tuple("485754433DE624AE", false));
    }
}
