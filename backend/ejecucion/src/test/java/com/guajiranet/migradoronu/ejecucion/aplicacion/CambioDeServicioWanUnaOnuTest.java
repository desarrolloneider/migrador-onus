package com.guajiranet.migradoronu.ejecucion.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import com.guajiranet.migradoronu.comun.puertos.ActorType;
import com.guajiranet.migradoronu.ejecucion.aplicacion.CambioDePerfilUnaOnu.Contexto;
import com.guajiranet.migradoronu.ejecucion.aplicacion.CambioDePerfilUnaOnu.Final;
import com.guajiranet.migradoronu.ejecucion.aplicacion.CambioDePerfilUnaOnu.Resultado;
import com.guajiranet.migradoronu.ejecucion.puertos.salida.GenieAcsPort;
import com.guajiranet.migradoronu.ejecucion.puertos.salida.GestionSmartOltPort;
import com.guajiranet.migradoronu.ejecucion.puertos.salida.OltConsolePort;

class CambioDeServicioWanUnaOnuTest {

    private static final String C1 = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.X_HW_SERVICELIST";
    private static final String C2 = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANIPConnection.1.X_HW_SERVICELIST";
    private static final TrabajoOnu TRABAJO = new TrabajoOnu(1, 2, "LOT-1", false, 3, "48575443FCFC55B3", "OLT_FONSECA", "0/5", 11, 55,
            "GUAJIRANET1", true, null, "SERVICIO_WAN");

    /** display ont wan-info de 48575443FD1281B3 (OLT_FONSECA 0/1/0 ONT 14) después del cambio, 16 de octubre. */
    private static final String WAN_CAMBIADA = """
              Name                       : 1_TR069_INTERNET_R_VID_41
              Service type               : Tr069, Internet
              Connection type            : IP routed
              IPv4 Connection status     : Connected
              IPv4 address               : 10.41.21.195
              Manage VLAN                : 41
              -----------------------------------------------------
              Service type               : Other
              Connection type            : IP routed
              IPv4 Connection status     : Connecting
              Manage VLAN                : 45
            """;
    private static final String WAN_SIN_CAMBIAR = """
              Name                       : 1_INTERNET_R_VID_41
              Service type               : Internet
              IPv4 address               : 10.41.21.195
              -----------------------------------------------------
              Name                       : 2_TR069_R_VID_45
              Service type               : Tr069
              IPv4 address               : 192.168.40.140
            """;

    /**
     * GenieACS de mentira: valores actuales, si acepta la etiqueta y si el cambio llega a verse (por la tarea o por el
     * preset con la etiqueta).
     */
    private static final class GenieFalso implements GenieAcsPort {
        final Map<String, String> valores = new LinkedHashMap<>();
        boolean aceptaEtiqueta = true;
        boolean aplicaElCambio = true;
        final List<Map<String, String>> cambios = new ArrayList<>();
        final List<Boolean> etiquetas = new ArrayList<>();

        GenieFalso(String v1, String v2) {
            valores.put(C1, v1);
            valores.put(C2, v2);
        }

        @Override
        public Optional<EstadoServicioWan> leer(String sn) {
            return Optional.of(new EstadoServicioWan("00259E-EG8041V5-" + sn, 1L, Map.copyOf(valores)));
        }

        @Override
        public boolean refrescar(String id, List<String> parametros) {
            return false;
        }

        @Override
        public boolean cambiar(String id, Map<String, String> nuevos) {
            cambios.add(nuevos);
            if (aplicaElCambio) {
                valores.putAll(nuevos);
            }
            return false;
        }

        @Override
        public boolean etiquetar(String id, String etiqueta, boolean poner) {
            if (!aceptaEtiqueta) {
                return false;
            }
            etiquetas.add(poner);
            // El preset hace el cambio en el siguiente reporte.
            if (poner && aplicaElCambio) {
                valores.put(C1, "TR069_INTERNET");
                valores.put(C2, "OTHER");
            }
            return true;
        }
    }

    private static final class SmartOltFalso implements GestionSmartOltPort {
        final List<String> desactivadas = new ArrayList<>();

        @Override
        public Optional<EstadoGestionOnu> leer(String sn) {
            return Optional.of(new EstadoGestionOnu("HWTCFCFC55B3", true, desactivadas.isEmpty() ? "DHCP" : "Inactive", 46, null));
        }

        @Override
        public void activarDhcp(String idExterno, int vlan) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void activarTr069(String idExterno, String perfil) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void desactivarGestion(String idExterno) {
            desactivadas.add(idExterno);
        }
    }

    /** Consola que solo contesta el display ont wan-info. */
    private static Supplier<OltConsolePort> consolaConWan(String wanInfo) {
        return () -> new OltConsolePort() {
            @Override
            public String ejecutar(String comando) {
                return comando.startsWith("display ont wan-info") ? wanInfo : "";
            }

            @Override
            public String prompt() {
                return "OLT#";
            }

            @Override
            public void close() {
                // Nada.
            }
        };
    }

    private static final Supplier<OltConsolePort> SIN_CONSOLA = () -> {
        throw new IllegalStateException("sin consola en la prueba");
    };

    private Resultado ejecutar(GenieFalso genie, SmartOltFalso smartOlt, boolean real) {
        return ejecutar(genie, smartOlt, real, SIN_CONSOLA);
    }

    private Resultado ejecutar(GenieFalso genie, SmartOltFalso smartOlt, boolean real, Supplier<OltConsolePort> consola) {
        CambioDePerfilUnaOnu perfil = new CambioDePerfilUnaOnu(entrada -> { }, d -> { });
        return new CambioDeServicioWanUnaOnu(genie, smartOlt, perfil, entrada -> { }, d -> { })
                .ejecutar(TRABAJO, real, new Contexto(ActorType.PROCESO_EJECUTOR, 3L, 2L, 1L), consola);
    }

    @Test
    void conLaEtiquetaElPresetHaceElCambioYSeQuitaLaVlan() {
        GenieFalso genie = new GenieFalso("INTERNET", "TR069");
        SmartOltFalso smartOlt = new SmartOltFalso();
        Resultado r = ejecutar(genie, smartOlt, true);
        assertThat(genie.etiquetas).startsWith(true);
        assertThat(genie.cambios).isEmpty();
        assertThat(smartOlt.desactivadas).containsExactly("HWTCFCFC55B3");
        // Sin consola en la prueba, el último paso (volver a poner el perfil) no se puede hacer: se informa en P08.
        assertThat(r.paso()).isEqualTo("P08");
        assertThat(r.pasos()).extracting(CambioDePerfilUnaOnu.Paso::codigo).containsSubsequence("P03", "P05", "P06", "P07", "P08");
    }

    @Test
    void sinEtiquetaMandaElCambioComoAntes() {
        GenieFalso genie = new GenieFalso("INTERNET", "TR069");
        genie.aceptaEtiqueta = false;
        SmartOltFalso smartOlt = new SmartOltFalso();
        ejecutar(genie, smartOlt, true);
        assertThat(genie.cambios).containsExactly(Map.of(C1, "TR069_INTERNET", C2, "OTHER"));
        assertThat(smartOlt.desactivadas).containsExactly("HWTCFCFC55B3");
    }

    @Test
    void siNoSeVeElCambioQuedaFallidaYLimpiaSinReiniciar() {
        GenieFalso genie = new GenieFalso("INTERNET", "TR069");
        genie.aplicaElCambio = false;
        SmartOltFalso smartOlt = new SmartOltFalso();
        Resultado r = ejecutar(genie, smartOlt, true, consolaConWan(WAN_SIN_CAMBIAR));
        assertThat(r.fin()).isEqualTo(Final.FALLIDA);
        assertThat(r.codigoFallo()).isEqualTo("FALLO_VERIFICACION");
        assertThat(smartOlt.desactivadas).containsExactly("HWTCFCFC55B3");
    }

    @Test
    void laOltMuestraElCambioAunqueGenieAcsEsteViejo() {
        GenieFalso genie = new GenieFalso("INTERNET", "TR069");
        genie.aplicaElCambio = false;
        SmartOltFalso smartOlt = new SmartOltFalso();
        Resultado r = ejecutar(genie, smartOlt, true, consolaConWan(WAN_CAMBIADA));
        assertThat(genie.etiquetas).isEmpty();
        assertThat(genie.cambios).isEmpty();
        assertThat(smartOlt.desactivadas).containsExactly("HWTCFCFC55B3");
        assertThat(r.pasos()).extracting(CambioDePerfilUnaOnu.Paso::codigo).doesNotContain("P05", "P06");
    }

    @Test
    void laSimulacionNoMandaNada() {
        GenieFalso genie = new GenieFalso("INTERNET", "TR069");
        SmartOltFalso smartOlt = new SmartOltFalso();
        assertThat(ejecutar(genie, smartOlt, false).fin()).isEqualTo(Final.SIMULADA);
        assertThat(genie.cambios).isEmpty();
        assertThat(genie.etiquetas).isEmpty();
        assertThat(smartOlt.desactivadas).isEmpty();
    }

    @Test
    void laQueYaEstabaListaSoloPierdeLaVlan() {
        GenieFalso genie = new GenieFalso("TR069_INTERNET", "OTHER");
        SmartOltFalso smartOlt = new SmartOltFalso();
        ejecutar(genie, smartOlt, true);
        assertThat(genie.cambios).isEmpty();
        assertThat(genie.etiquetas).isEmpty();
        assertThat(smartOlt.desactivadas).hasSize(1);
    }

    @Test
    void sinLasDosConexionesEsperadasLaDejaSinVlan() {
        GenieFalso genie = new GenieFalso("TR069_INTERNET", "TR069_INTERNET");
        SmartOltFalso smartOlt = new SmartOltFalso();
        assertThat(ejecutar(genie, smartOlt, true).codigoFallo()).isEqualTo("FALLO_FOTO_PREVIA");
        assertThat(genie.cambios).isEmpty();
        assertThat(genie.etiquetas).isEmpty();
        assertThat(smartOlt.desactivadas).containsExactly("HWTCFCFC55B3");
    }

    @Test
    void leeLasWanDeLaOlt() {
        assertThat(CambioDeServicioWanUnaOnu.listoEnOlt(WAN_CAMBIADA)).isTrue();
        assertThat(CambioDeServicioWanUnaOnu.listoEnOlt(WAN_SIN_CAMBIAR)).isFalse();
        assertThat(CambioDeServicioWanUnaOnu.listoEnOlt("")).isFalse();
        assertThat(CambioDeServicioWanUnaOnu.servicios(WAN_CAMBIADA)).isEqualTo("Tr069, Internet | Other");
        assertThat(CambioDeServicioWanUnaOnu.comandoWan(TRABAJO)).isEqualTo("display ont wan-info 0/5 11 55");
    }
}
