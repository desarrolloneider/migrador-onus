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
    private final Map<String, String> servicioTerminado = new HashMap<>();

    private final ClasificarOnusUseCase clasificar = new ClasificarOnusUseCase(
            new OnuInventoryPort() {
                public List<OnuCandidate> listOnus(String marca, String olt, String tarjeta, Integer pon) {
                    return onus;
                }

                public List<OnuCandidate> findBySn(String sn) {
                    return onus.stream().filter(o -> FormatoSn.normalizar(o.rawSn()).orElse(o.rawSn()).equalsIgnoreCase(FormatoSn.normalizar(sn).orElse(sn))).toList();
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

                public Map<String, String> servicioWanTerminado() {
                    return servicioTerminado;
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

    @Test
    void conLaOltSoloEntranLasDelPerfilOrigen() {
        Map<String, String> enOlt = Map.of("0/0/1:26", "GUAJIRANET", "0/0/1:27", "GUAJIRANET1");
        ClasificarOnusUseCase conOlt = conPerfilesDeOlt(Optional.of(enOlt));
        onus.add(onu("HWTC00000026", "0/0", 26, true, "10.1.1.26", null));
        onus.add(onu("HWTC00000027", "0/0", 27, true, "10.1.1.27", null));
        onus.add(onu("HWTC00000028", "0/0", 28, true, "10.1.1.28", null));
        contratos.add(new Subscriber("A", "C-26", "10.1.1.26", "enabled"));
        contratos.add(new Subscriber("B", "C-27", "10.1.1.27", "enabled"));
        contratos.add(new Subscriber("C", "C-28", "10.1.1.28", "enabled"));
        Map<String, Classification> r = new HashMap<>();
        conOlt.classify(new ClassifyOnusCommand("Huawei", null, null, null)).forEach(c -> r.put(c.onu().rawSn(), c));
        assertThat(r.get("HWTC00000026").group()).isEqualTo(ClassificationGroup.ENTRA);
        assertThat(r.get("HWTC00000026").onu().perfilTr069()).isEqualTo("GUAJIRANET");
        assertThat(r.get("HWTC00000027").group()).isEqualTo(ClassificationGroup.YA_TIENE_PERFIL);
        // Por defecto también entran las que no tienen perfil.
        assertThat(r.get("HWTC00000028").group()).isEqualTo(ClassificationGroup.ENTRA);
    }

    @Test
    void unaOnuConOtroPerfilQuedaFuera() {
        ClasificarOnusUseCase conOlt = conPerfilesDeOlt(Optional.of(Map.of("0/0/1:26", "WISPROACSTEST")));
        onus.add(onu("HWTC00000026", "0/0", 26, true, "10.1.1.26", null));
        contratos.add(new Subscriber("A", "C-26", "10.1.1.26", "enabled"));
        assertThat(conOlt.classify(new ClassifyOnusCommand("Huawei", null, null, null)).getFirst().reason())
                .isEqualTo(ExclusionReason.EXC_OTRO_PERFIL);
    }

    @Test
    void unaOnuConElPerfilQueDejaSmartOltEntraAlLoteDePerfil() {
        // SmartOLT le pone su perfil al activar la gestión en la VLAN 46; después el lote de perfil le pone GUAJIRANET1.
        ClasificarOnusUseCase conOlt = conPerfilesDeOlt(Optional.of(Map.of("0/0/1:27", "SmartOLT")));
        onus.add(onu("HWTC00000027", "0/0", 27, true, "10.1.1.27", null));
        contratos.add(new Subscriber("A", "C-27", "10.1.1.27", "enabled"));
        assertThat(conOlt.classify(new ClassifyOnusCommand("Huawei", null, null, null)).getFirst().group())
                .isEqualTo(ClassificationGroup.ENTRA);
    }

    @Test
    void siLaOltNoSePudoLeerNoSeAdivina() {
        onus.add(onu("HWTC00000026", "0/0", 26, true, "10.1.1.26", null));
        contratos.add(new Subscriber("A", "C-26", "10.1.1.26", "enabled"));
        assertThat(conPerfilesDeOlt(Optional.empty()).classify(new ClassifyOnusCommand("Huawei", null, null, null)).getFirst().reason())
                .isEqualTo(ExclusionReason.EXC_PERFIL_NO_LEIDO);
    }

    @Test
    void escogiendoSinPerfilYOnusPuntuales() {
        ClasificarOnusUseCase conOlt = conPerfilesDeOlt(Optional.of(Map.of("0/0/1:26", "GUAJIRANET")));
        onus.add(onu("HWTC00000026", "0/0", 26, true, "10.1.1.26", null));
        onus.add(onu("HWTC00000028", "0/0", 28, true, "10.1.1.28", null));
        onus.add(onu("HWTC00000029", "0/0", 29, true, "10.1.1.29", null));
        contratos.add(new Subscriber("A", "C-26", "10.1.1.26", "enabled"));
        contratos.add(new Subscriber("C", "C-28", "10.1.1.28", "enabled"));
        contratos.add(new Subscriber("D", "C-29", "10.1.1.29", "enabled"));
        Map<String, Classification> r = new HashMap<>();
        conOlt.classify(new ClassifyOnusCommand("Huawei", null, null, null, List.of(ClassifyOnusCommand.SIN_PERFIL),
                List.of("HWTC00000026", "485754430000002" + "8"))).forEach(c -> r.put(c.onu().rawSn(), c));
        assertThat(r).doesNotContainKey("HWTC00000029");
        assertThat(r.get("HWTC00000028").group()).isEqualTo(ClassificationGroup.ENTRA);
        // Con ONUs escogidas no se lee la OLT entera (rápido): entra y el ejecutor la lee en la OLT antes de tocarla.
        assertThat(r.get("HWTC00000026").group()).isEqualTo(ClassificationGroup.ENTRA);
    }

    private ClasificarOnusUseCase conPerfilesDeOlt(Optional<Map<String, String>> perfiles) {
        return new ClasificarOnusUseCase(
                new OnuInventoryPort() {
                    public List<OnuCandidate> listOnus(String marca, String olt, String tarjeta, Integer pon) {
                        return onus;
                    }

                    public List<OnuCandidate> findBySn(String sn) {
                        return onus.stream().filter(o -> FormatoSn.normalizar(o.rawSn()).orElse(o.rawSn()).equalsIgnoreCase(FormatoSn.normalizar(sn).orElse(sn))).toList();
                    }
                },
                new SubscriberDirectoryPort() {
                    public List<Subscriber> listAll() {
                        return contratos;
                    }

                    public List<Subscriber> findByIp(String ip) {
                        return List.of();
                    }
                },
                new OltSettingsQueryPort() {
                    public Set<String> allowedBoards(String olt) {
                        return Set.of("0/0");
                    }

                    public boolean consoleAccessConfigured(String olt) {
                        return true;
                    }
                },
                new OnuParticipationQueryPort() {
                    public Optional<String> activeBatchNumberOf(String sn) {
                        return Optional.empty();
                    }

                    public Optional<UnreviewedFailure> unreviewedFailureOf(String sn) {
                        return Optional.empty();
                    }
                },
                () -> "GUAJIRANET1", Set.of("Huawei"), olt -> perfiles, () -> "GUAJIRANET");
    }

    @Test
    void elServicioWanDejaEntrarAunqueNoSeReporteAGenieAcs() {
        java.time.Instant ahora = java.time.Instant.parse("2026-10-14T16:00:00Z");
        var gestion = new com.guajiranet.migradoronu.conciliacion.dominio.GestionOnu("DHCP", 46, List.of(), false);
        onus.add(new OnuCandidate("48575443AAAA0001", "Huawei", "EG8041V5", new OnuPosition("MA5683T", "0/0", 1, 1), true, null, null, gestion));
        onus.add(new OnuCandidate("48575443AAAA0002", "Huawei", "EG8041V5", new OnuPosition("MA5683T", "0/0", 1, 2), true, null, null, gestion));
        onus.add(new OnuCandidate("48575443AAAA0003", "Huawei", "EG8041V5", new OnuPosition("MA5683T", "0/0", 1, 3), true, null, null, gestion));
        Map<String, java.time.Instant> reportes = Map.of("48575443AAAA0001", ahora.minusSeconds(30),
                "48575443AAAA0002", java.time.Instant.parse("2026-04-25T17:30:39Z"));
        clasificar.conAcs(sn -> Optional.ofNullable(reportes.get(sn)), java.time.Clock.fixed(ahora, java.time.ZoneOffset.UTC));
        Map<String, Classification> r = new HashMap<>();
        clasificar.classify(new ClassifyOnusCommand("Huawei", null, null, null, null, null, "SERVICIO_WAN"))
                .forEach(c -> r.put(c.onu().rawSn(), c));
        // Todas entran: las que no se reportan se reinician al ejecutar y, si siguen sin responder, se dejan limpias.
        assertThat(r.values()).allSatisfy(c -> assertThat(c.group()).isEqualTo(ClassificationGroup.ENTRA));
    }

    @Test
    void elPasoUnoDejaFueraLasQueYaTerminaronElServicioWan() {
        onus.add(new OnuCandidate("48575443AAAA0001", "Huawei", "EG8041V5", new OnuPosition("MA5683T", "0/0", 1, 1), true, null, null, null));
        onus.add(new OnuCandidate("48575443AAAA0002", "Huawei", "EG8041V5", new OnuPosition("MA5683T", "0/0", 1, 2), true, null, null, null));
        servicioTerminado.put("48575443AAAA0002", "LOT-2026-000064");
        Map<String, Classification> r = new HashMap<>();
        clasificar.classify(new ClassifyOnusCommand("Huawei", null, null, null, null, null, ClassifyOnusCommand.VLAN_GESTION))
                .forEach(c -> r.put(c.onu().rawSn(), c));
        assertThat(r.get("48575443AAAA0001").reason()).isNull();
        assertThat(r.get("48575443AAAA0002").message()).contains("ya terminó los 3 pasos").contains("LOT-2026-000064");
    }

    @Test
    void elPasoUnoDejaFueraLasQueGenieAcsYaTieneListas() {
        onus.add(new OnuCandidate("48575443AAAA0003", "Huawei", "EG8041V5", new OnuPosition("MA5683T", "0/0", 1, 3), true, null, null, null));
        clasificar.conAcs(new com.guajiranet.migradoronu.conciliacion.puertos.salida.UltimoReporteAcsPort() {
            public Optional<java.time.Instant> ultimoReporte(String sn) {
                return Optional.empty();
            }

            public Set<String> conServicioWanListo() {
                return Set.of("48575443AAAA0003");
            }
        }, java.time.Clock.systemUTC());
        Classification c = clasificar.classify(new ClassifyOnusCommand("Huawei", null, null, null, null, null, ClassifyOnusCommand.VLAN_GESTION)).getFirst();
        assertThat(c.message()).contains("ya terminó los 3 pasos").contains("GenieACS");
    }
}
