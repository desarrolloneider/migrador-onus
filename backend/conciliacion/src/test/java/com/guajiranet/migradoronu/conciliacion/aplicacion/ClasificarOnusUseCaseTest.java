package com.guajiranet.migradoronu.conciliacion.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.guajiranet.migradoronu.conciliacion.dominio.Classification;
import com.guajiranet.migradoronu.conciliacion.dominio.ClassificationGroup;
import com.guajiranet.migradoronu.conciliacion.dominio.ExclusionReason;
import com.guajiranet.migradoronu.conciliacion.dominio.FormatoSn;
import com.guajiranet.migradoronu.conciliacion.dominio.OnuCandidate;
import com.guajiranet.migradoronu.conciliacion.dominio.OnuPosition;
import com.guajiranet.migradoronu.conciliacion.dominio.Subscriber;
import com.guajiranet.migradoronu.conciliacion.dominio.UnreviewedFailure;
import com.guajiranet.migradoronu.conciliacion.puertos.salida.OltSettingsQueryPort;
import com.guajiranet.migradoronu.conciliacion.puertos.salida.OnuInventoryPort;
import com.guajiranet.migradoronu.conciliacion.puertos.salida.OnuParticipationQueryPort;
import com.guajiranet.migradoronu.conciliacion.puertos.salida.SubscriberDirectoryPort;

class ClasificarOnusUseCaseTest {

    private final List<OnuCandidate> onus = new ArrayList<>();
    private final List<Subscriber> contratos = new ArrayList<>();
    private final Map<String, String> enOtroLote = new HashMap<>();
    private boolean accesoConfigurado = true;

    private final ClasificarOnusUseCase clasificar = new ClasificarOnusUseCase(
            new OnuInventoryPort() {
                public List<OnuCandidate> listOnus(String marca, String olt, String tarjeta, Integer pon) {
                    return onus;
                }

                public List<OnuCandidate> findBySn(String sn) {
                    return onus.stream().filter(o -> o.rawSn().equalsIgnoreCase(sn)).toList();
                }
            },
            new SubscriberDirectoryPort() {
                public List<Subscriber> listAll() {
                    return contratos;
                }

                public List<Subscriber> findByIp(String ip) {
                    return contratos.stream().filter(c -> ip.equals(c.ip())).toList();
                }
            },
            new OltSettingsQueryPort() {
                public Set<String> allowedBoards(String olt) {
                    return Set.of("0/0");
                }

                public boolean consoleAccessConfigured(String olt) {
                    return accesoConfigurado;
                }
            },
            new OnuParticipationQueryPort() {
                public Optional<String> activeBatchNumberOf(String sn) {
                    return Optional.ofNullable(enOtroLote.get(sn));
                }

                public Optional<UnreviewedFailure> unreviewedFailureOf(String sn) {
                    return Optional.empty();
                }
            },
            () -> "GUAJIRANET1", Set.of("Huawei"));

    private static OnuCandidate onu(String sn, String tarjeta, int id, boolean enLinea, String ip, String perfil) {
        return new OnuCandidate(sn, "Huawei", "HG8145V5", new OnuPosition("MA5683T", tarjeta, 1, id), enLinea, ip, perfil);
    }

    private Classification unica() {
        List<Classification> r = clasificar.classify(new ClassifyOnusCommand("Huawei", null, null, null));
        assertThat(r).hasSize(1);
        return r.getFirst();
    }

    @Test
    void entraCuandoTodoCuadra() {
        onus.add(onu("HWTC1A2B3C4D", "0/0", 26, true, "10.1.1.5", "GUAJIRANET"));
        contratos.add(new Subscriber("Ana", "C-1", "10.1.1.5", "enabled"));
        Classification c = unica();
        assertThat(c.group()).isEqualTo(ClassificationGroup.ENTRA);
        assertThat(c.sn()).isEqualTo("485754431A2B3C4D");
        assertThat(c.subscriber().contrato()).isEqualTo("C-1");
    }

    @Test
    void yaTieneElPerfilSinImportarMayusculas() {
        onus.add(onu("HWTC1A2B3C4D", "0/0", 26, true, "10.1.1.5", "guajiranet1"));
        assertThat(unica().group()).isEqualTo(ClassificationGroup.YA_TIENE_PERFIL);
    }

    @Test
    void duplicadoSinUnoEnLineaQuedaFuera() {
        onus.add(onu("HWTC1A2B3C4D", "0/0", 26, false, "10.1.1.5", null));
        onus.add(onu("485754431A2B3C4D", "0/0", 27, false, "10.1.1.5", null));
        Classification c = unica();
        assertThat(c.reason()).isEqualTo(ExclusionReason.EXC_SN_DUPLICADO_SIN_VIGENTE);
        assertThat(c.duplicates()).hasSize(2);
    }

    @Test
    void duplicadoConUnoEnLineaUsaEseYGuardaElOtro() {
        onus.add(onu("HWTC1A2B3C4D", "0/0", 26, false, "10.1.1.5", null));
        onus.add(onu("HWTC1A2B3C4D", "0/0", 27, true, "10.1.1.5", null));
        contratos.add(new Subscriber("Ana", "C-1", "10.1.1.5", "enabled"));
        Classification c = unica();
        assertThat(c.group()).isEqualTo(ClassificationGroup.ENTRA);
        assertThat(c.onu().position().onuId()).isEqualTo(27);
        assertThat(c.duplicates()).singleElement().satisfies(d -> assertThat(d.position().onuId()).isEqualTo(26));
    }

    @Test
    void motivosDeExclusionEnOrden() {
        onus.add(onu("ZZ", "0/0", 1, true, "10.0.0.1", null));
        onus.add(onu("HWTC00000002", "0/0", 2, false, "10.0.0.2", null));
        onus.add(onu("HWTC00000003", "0/0", 3, true, null, null));
        onus.add(onu("HWTC00000004", "0/0", 4, true, "10.0.0.4", null));
        onus.add(onu("HWTC00000005", "0/0", 5, true, "10.0.0.5", null));
        onus.add(onu("HWTC00000006", "0/9", 6, true, "10.0.0.6", null));
        onus.add(onu("HWTC00000007", "0/0", 7, true, "10.0.0.7", null));
        contratos.add(new Subscriber("A", "C-5a", "10.0.0.5", "enabled"));
        contratos.add(new Subscriber("B", "C-5b", "10.0.0.5", "enabled"));
        contratos.add(new Subscriber("C", "C-6", "10.0.0.6", "enabled"));
        contratos.add(new Subscriber("D", "C-7", "10.0.0.7", "enabled"));
        enOtroLote.put(FormatoSn.normalizar("HWTC00000007").orElseThrow(), "LOT-2026-000001");
        Map<String, ExclusionReason> motivos = new HashMap<>();
        clasificar.classify(new ClassifyOnusCommand("Huawei", null, null, null)).forEach(c -> motivos.put(c.onu().rawSn(), c.reason()));
        assertThat(motivos).containsEntry("ZZ", ExclusionReason.EXC_SN_FORMATO)
                .containsEntry("HWTC00000002", ExclusionReason.EXC_FUERA_DE_LINEA)
                .containsEntry("HWTC00000003", ExclusionReason.EXC_SIN_IP_SMARTOLT)
                .containsEntry("HWTC00000004", ExclusionReason.EXC_IP_NO_EN_WISPRO)
                .containsEntry("HWTC00000005", ExclusionReason.EXC_IP_EN_VARIOS_CONTRATOS)
                .containsEntry("HWTC00000006", ExclusionReason.EXC_TARJETA_NO_PERMITIDA)
                .containsEntry("HWTC00000007", ExclusionReason.EXC_EN_OTRO_LOTE);
    }

    @Test
    void oltSinAccesoQuedaFuera() {
        accesoConfigurado = false;
        onus.add(onu("HWTC1A2B3C4D", "0/0", 26, true, "10.1.1.5", null));
        contratos.add(new Subscriber("Ana", "C-1", "10.1.1.5", "enabled"));
        assertThat(unica().reason()).isEqualTo(ExclusionReason.EXC_OLT_SIN_ACCESO_CONFIGURADO);
    }

    @Test
    void formatoComunDelSn() {
        assertThat(FormatoSn.normalizar("hwtc-1a2b3c4d")).contains("485754431A2B3C4D");
        assertThat(FormatoSn.normalizar("485754431a2b3c4d")).contains("485754431A2B3C4D");
        assertThat(FormatoSn.normalizar("HWTC1A2B")).isEmpty();
    }
}
