package com.guajiranet.migradoronu.ejecucion.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.guajiranet.migradoronu.comun.puertos.AuditEntry;
import com.guajiranet.migradoronu.ejecucion.aplicacion.CambioDePerfilUnaOnu.Objetivo;
import com.guajiranet.migradoronu.ejecucion.aplicacion.CambioDePerfilUnaOnu.Resultado;
import com.guajiranet.migradoronu.ejecucion.puertos.salida.OltConsolePort;

class CambioDePerfilUnaOnuTest {

    private final List<AuditEntry> auditados = new ArrayList<>();
    private final CambioDePerfilUnaOnu cambio = new CambioDePerfilUnaOnu(auditados::add);

    /** Una OLT de mentira que se comporta como OLT-CONEJO. */
    static final class OltFalsa implements OltConsolePort {
        final List<String> recibidos = new ArrayList<>();
        int perfil = 2;
        boolean enLinea = true;
        boolean rechazar = false;
        boolean sinSmartOlt = false;
        int ocupadaVeces = 0;
        String prompt = "OLT-CONEJO>";

        @Override
        public String ejecutar(String comando) {
            recibidos.add(comando);
            switch (comando) {
                case "enable" -> prompt = "OLT-CONEJO#";
                case "config" -> prompt = "OLT-CONEJO(config)#";
                case "interface gpon 0/0" -> prompt = "OLT-CONEJO(config-if-gpon-0/0)#";
                case "quit" -> prompt = prompt.contains("-if-") ? "OLT-CONEJO(config)#" : "OLT-CONEJO#";
                default -> { }
            }
            if (comando.equals("display ont tr069-server-profile all") && sinSmartOlt) {
                return """
                         Profile ID     Profile Name                     Binding Times
                         2              GUAJIRANET                       0
                         3              GUAJIRANET1                      10
                        """;
            }
            if (comando.equals("display ont tr069-server-profile all")) {
                return """
                         Profile ID     Profile Name                     Binding Times
                         1              SmartOLT                         4
                         2              GUAJIRANET                       6
                         3              GUAJIRANET1                      10
                        """;
            }
            if (comando.startsWith("display ont info")) {
                return """
                          Run state               : %s
                          SN                      : 48575443FCAA95B0 (HWTC-FCAA95B0)
                          Description             : 1 10.0.0.9 (Cliente
                                                    Secreto)_P3
                          TR069 server profile ID      : %s
                          TR069 server profile name    : %s
                        """.formatted(enLinea ? "online" : "offline", perfil == 0 ? "-" : String.valueOf(perfil),
                                perfil == 0 ? "-" : perfil == 3 ? "GUAJIRANET1" : perfil == 1 ? "SmartOLT" : "GUAJIRANET");
            }
            if (comando.startsWith("ont tr069-server-config")) {
                if (ocupadaVeces > 0) {
                    ocupadaVeces--;
                    return "  Failure: System is busy, please retry after a while";
                }
                if (rechazar) {
                    return "  % Failure: The ONT does not exist";
                }
                perfil = Integer.parseInt(comando.substring(comando.lastIndexOf(' ') + 1));
            }
            return comando.equals("save") ? "The data of the system is saved completely" : "";
        }

        @Override
        public String prompt() {
            return prompt;
        }

        @Override
        public void close() {
        }
    }

    private static Objetivo objetivo(boolean permitirFueraDeLinea) {
        return new Objetivo("OLT-CONEJO", 0, 0, 6, 2, List.of("GUAJIRANET"), "GUAJIRANET1", true, permitirFueraDeLinea, "48575443FCAA95B0");
    }

    @Test
    void siEnEsaPosicionHayOtroEquipoNoSeToca() {
        OltFalsa olt = new OltFalsa();
        Objetivo otro = new Objetivo("OLT-CONEJO", 0, 0, 6, 2, List.of("GUAJIRANET"), "GUAJIRANET1", true, false, "4857544300000000");
        Resultado r = cambio.ejecutar(otro, true, () -> olt);
        assertThat(r.fin()).isEqualTo(CambioDePerfilUnaOnu.Final.EXCLUIDA);
        assertThat(r.mensaje()).contains("otro equipo");
        assertThat(olt.recibidos).doesNotContain("config");
    }

    @Test
    void cambioRealCompleto() {
        OltFalsa olt = new OltFalsa();
        Resultado r = cambio.ejecutar(objetivo(false), true, () -> olt);
        assertThat(r.completado()).isTrue();
        assertThat(olt.perfil).isEqualTo(3);
        assertThat(olt.recibidos).containsSubsequence("config", "interface gpon 0/0", "ont tr069-server-config 6 2 profile-id 3",
                "quit", "display ont info 0 0 6 2", "save");
    }

    @Test
    void laSimulacionNoMandaNingunComandoDeCambio() {
        OltFalsa olt = new OltFalsa();
        Resultado r = cambio.ejecutar(objetivo(false), false, () -> olt);
        assertThat(r.completado()).isFalse();
        assertThat(olt.perfil).isEqualTo(2);
        assertThat(olt.recibidos).containsExactly("enable", "display ont tr069-server-profile all", "display ont info 0 0 6 2");
        assertThat(r.pasos()).extracting(CambioDePerfilUnaOnu.Paso::comando).contains("ont tr069-server-config 6 2 profile-id 3", "save");
    }

    @Test
    void fueraDeLineaNoSeTocaSalvoPermisoExplicito() {
        OltFalsa olt = new OltFalsa();
        olt.enLinea = false;
        assertThat(cambio.ejecutar(objetivo(false), true, () -> olt).mensaje()).contains("fuera de línea");
        assertThat(olt.recibidos).doesNotContain("config");
        assertThat(cambio.ejecutar(objetivo(true), true, () -> olt).completado()).isTrue();
    }

    @Test
    void unRechazoSeDetieneSinGuardar() {
        OltFalsa olt = new OltFalsa();
        olt.rechazar = true;
        Resultado r = cambio.ejecutar(objetivo(false), true, () -> olt);
        assertThat(r.completado()).isFalse();
        assertThat(r.mensaje()).isNotBlank();
        assertThat(r.codigoFallo()).isEqualTo("FALLO_OLT_RECHAZO");
        assertThat(olt.recibidos).doesNotContain("save");
    }

    @Test
    void siYaTieneElPerfilYLaVlanDeGestionLoReaplica() {
        OltFalsa olt = new OltFalsa();
        olt.perfil = 3;
        Objetivo conVlan = new Objetivo("OLT-CONEJO", 0, 0, 6, 2, List.of("GUAJIRANET"), "GUAJIRANET1", true, false, "48575443FCAA95B0", () -> true);
        Resultado r = cambio.ejecutar(conVlan, true, () -> olt);
        assertThat(r.fin()).isEqualTo(CambioDePerfilUnaOnu.Final.COMPLETADA);
        assertThat(r.mensaje()).contains("reaplicado");
        assertThat(olt.recibidos).containsSubsequence("ont tr069-server-config 6 2 profile-id 1", "ont tr069-server-config 6 2 profile-id 3", "save");
        assertThat(olt.perfil).isEqualTo(3);
    }

    @Test
    void sinElPerfilSmartOltReaplicaPasandoPorGuajiranet() {
        OltFalsa olt = new OltFalsa();
        olt.perfil = 3;
        olt.sinSmartOlt = true;
        Objetivo conVlan = new Objetivo("OLT-CONEJO", 0, 0, 6, 2, List.of("GUAJIRANET"), "GUAJIRANET1", true, false, "48575443FCAA95B0", () -> true);
        assertThat(cambio.ejecutar(conVlan, true, () -> olt).fin()).isEqualTo(CambioDePerfilUnaOnu.Final.COMPLETADA);
        assertThat(olt.recibidos).containsSubsequence("ont tr069-server-config 6 2 profile-id 2", "ont tr069-server-config 6 2 profile-id 3");
    }

    @Test
    void siYaTieneElPerfilYNoTieneLaVlanNoHaceNada() {
        OltFalsa olt = new OltFalsa();
        olt.perfil = 3;
        Objetivo sinVlan = new Objetivo("OLT-CONEJO", 0, 0, 6, 2, List.of("GUAJIRANET"), "GUAJIRANET1", true, false, "48575443FCAA95B0", () -> false);
        assertThat(cambio.ejecutar(sinVlan, true, () -> olt).fin()).isEqualTo(CambioDePerfilUnaOnu.Final.YA_TENIA);
        assertThat(olt.recibidos).doesNotContain("config");
    }

    @Test
    void siYaTieneElPerfilNoHaceNada() {
        OltFalsa olt = new OltFalsa();
        olt.perfil = 3;
        assertThat(cambio.ejecutar(objetivo(false), true, () -> olt).fin()).isEqualTo(CambioDePerfilUnaOnu.Final.YA_TENIA);
        assertThat(olt.recibidos).doesNotContain("config");
    }

    @Test
    void laAuditoriaNoGuardaLaDescripcion() {
        cambio.ejecutar(objetivo(false), true, OltFalsa::new);
        assertThat(auditados).isNotEmpty();
        assertThat(auditados.toString()).doesNotContain("Secreto", "10.0.0.9");
    }

    @Test
    void unaOnuSinPerfilSoloSeCambiaSiElLoteLasIncluye() {
        OltFalsa olt = new OltFalsa();
        olt.perfil = 0;
        Objetivo soloGuajiranet = new Objetivo("OLT-CONEJO", 0, 0, 6, 2, List.of("GUAJIRANET"), "GUAJIRANET1", true, false, "48575443FCAA95B0");
        assertThat(cambio.ejecutar(soloGuajiranet, true, () -> olt).fin()).isEqualTo(CambioDePerfilUnaOnu.Final.EXCLUIDA);
        assertThat(olt.recibidos).doesNotContain("config");
        Objetivo conSinPerfil = new Objetivo("OLT-CONEJO", 0, 0, 6, 2, List.of("GUAJIRANET", CambioDePerfilUnaOnu.SIN_PERFIL), "GUAJIRANET1",
                true, false, "48575443FCAA95B0");
        Resultado r = cambio.ejecutar(conSinPerfil, true, () -> olt);
        assertThat(r.completado()).isTrue();
        assertThat(r.mensaje()).contains("sin perfil a GUAJIRANET1");
    }

    @Test
    void siLaOltEstaOcupadaEsperaYReintenta() {
        List<java.time.Duration> esperas = new ArrayList<>();
        CambioDePerfilUnaOnu paciente = new CambioDePerfilUnaOnu(auditados::add, esperas::add);
        OltFalsa olt = new OltFalsa();
        olt.ocupadaVeces = 2;
        Resultado r = paciente.ejecutar(objetivo(false), true, () -> olt);
        assertThat(r.completado()).isTrue();
        assertThat(esperas).hasSize(2);
        assertThat(olt.recibidos.stream().filter(c -> c.startsWith("ont tr069-server-config")).count()).isEqualTo(3);
    }

    @Test
    void siSigueOcupadaFallaSinGuardar() {
        CambioDePerfilUnaOnu paciente = new CambioDePerfilUnaOnu(auditados::add, d -> { });
        OltFalsa olt = new OltFalsa();
        olt.ocupadaVeces = 100;
        Resultado r = paciente.ejecutar(objetivo(false), true, () -> olt);
        assertThat(r.completado()).isFalse();
        assertThat(r.codigoFallo()).isEqualTo("FALLO_SISTEMA_NO_RESPONDE");
        assertThat(r.mensaje()).contains("ocupada");
        assertThat(olt.recibidos).doesNotContain("save");
        assertThat(olt.perfil).isEqualTo(2);
    }
}
