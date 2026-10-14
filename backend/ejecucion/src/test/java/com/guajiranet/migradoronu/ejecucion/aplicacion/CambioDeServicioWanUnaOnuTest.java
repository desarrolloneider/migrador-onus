package com.guajiranet.migradoronu.ejecucion.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    /** GenieACS de mentira: valores actuales, si la ONU responde y si el cambio llega a verse. */
    private static final class GenieFalso implements GenieAcsPort {
        final Map<String, String> valores = new LinkedHashMap<>();
        boolean responde = true;
        boolean aplicaElCambio = true;
        final List<Map<String, String>> cambios = new ArrayList<>();

        GenieFalso(String v1, String v2) {
            valores.put(C1, v1);
            valores.put(C2, v2);
        }

        @Override
        public Optional<EstadoServicioWan> leer(String sn) {
            return Optional.of(new EstadoServicioWan("00259E-EG8041V5-" + sn, 1L, valores));
        }

        @Override
        public boolean refrescar(String id, List<String> parametros) {
            return responde;
        }

        @Override
        public boolean cambiar(String id, Map<String, String> nuevos) {
            cambios.add(nuevos);
            if (aplicaElCambio) {
                valores.putAll(nuevos);
            }
            return false;
        }
    }

    private static final class SmartOltFalso implements GestionSmartOltPort {
        final List<String> desactivadas = new ArrayList<>();
        final List<String> reiniciadas = new ArrayList<>();
        Runnable alReiniciar = () -> { };

        @Override
        public void reiniciar(String idExterno) {
            reiniciadas.add(idExterno);
            alReiniciar.run();
        }

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

    private Resultado ejecutar(GenieFalso genie, SmartOltFalso smartOlt, boolean real) {
        CambioDePerfilUnaOnu perfil = new CambioDePerfilUnaOnu(entrada -> { }, d -> { });
        return new CambioDeServicioWanUnaOnu(genie, smartOlt, perfil, entrada -> { }, d -> { })
                .ejecutar(TRABAJO, real, new Contexto(ActorType.PROCESO_EJECUTOR, 3L, 2L, 1L), () -> {
                    throw new IllegalStateException("sin consola en la prueba");
                });
    }

    @Test
    void siLaOnuNoRespondeAGenieAcsLaDejaSinVlan() {
        GenieFalso genie = new GenieFalso("INTERNET", "TR069");
        genie.responde = false;
        SmartOltFalso smartOlt = new SmartOltFalso();
        Resultado r = ejecutar(genie, smartOlt, true);
        assertThat(r.fin()).isEqualTo(Final.FALLIDA);
        assertThat(r.codigoFallo()).isEqualTo("FALLO_SISTEMA_NO_RESPONDE");
        assertThat(genie.cambios).isEmpty();
        assertThat(smartOlt.desactivadas).containsExactly("HWTCFCFC55B3");
    }

    @Test
    void laSimulacionNoMandaNada() {
        GenieFalso genie = new GenieFalso("INTERNET", "TR069");
        SmartOltFalso smartOlt = new SmartOltFalso();
        assertThat(ejecutar(genie, smartOlt, false).fin()).isEqualTo(Final.SIMULADA);
        assertThat(genie.cambios).isEmpty();
        assertThat(smartOlt.desactivadas).isEmpty();
    }

    @Test
    void siGenieAcsNoConfirmaElCambioLaDejaSinVlan() {
        GenieFalso genie = new GenieFalso("INTERNET", "TR069");
        genie.aplicaElCambio = false;
        SmartOltFalso smartOlt = new SmartOltFalso();
        Resultado r = ejecutar(genie, smartOlt, true);
        assertThat(r.fin()).isEqualTo(Final.FALLIDA);
        assertThat(r.codigoFallo()).isEqualTo("FALLO_VERIFICACION");
        assertThat(genie.cambios).hasSize(1);
        assertThat(smartOlt.desactivadas).containsExactly("HWTCFCFC55B3");
    }

    @Test
    void siNoConfirmaEn30SegundosLaReiniciaUnaVezYSigue() {
        GenieFalso genie = new GenieFalso("INTERNET", "TR069");
        genie.aplicaElCambio = false;
        SmartOltFalso smartOlt = new SmartOltFalso();
        // Al reiniciar, la ONU se reporta con el cambio pendiente aplicado.
        smartOlt.alReiniciar = () -> genie.valores.putAll(Map.of(C1, "TR069_INTERNET", C2, "OTHER"));
        Resultado r = ejecutar(genie, smartOlt, true);
        assertThat(smartOlt.reiniciadas).containsExactly("HWTCFCFC55B3");
        assertThat(smartOlt.desactivadas).containsExactly("HWTCFCFC55B3");
        assertThat(r.paso()).isEqualTo("P08");
    }

    @Test
    void cambiaUnaVezVerificaYQuitaLaVlan() {
        GenieFalso genie = new GenieFalso("INTERNET", "TR069");
        SmartOltFalso smartOlt = new SmartOltFalso();
        Resultado r = ejecutar(genie, smartOlt, true);
        assertThat(genie.cambios).containsExactly(Map.of(C1, "TR069_INTERNET", C2, "OTHER"));
        assertThat(smartOlt.desactivadas).containsExactly("HWTCFCFC55B3");
        // Sin consola en la prueba, el último paso (volver a poner el perfil) no se puede hacer: se informa en P08.
        assertThat(r.paso()).isEqualTo("P08");
        assertThat(r.pasos()).extracting(CambioDePerfilUnaOnu.Paso::codigo).containsSubsequence("P03", "P05", "P06", "P07", "P08");
    }

    @Test
    void laQueYaEstabaListaSoloPierdeLaVlan() {
        GenieFalso genie = new GenieFalso("TR069_INTERNET", "OTHER");
        SmartOltFalso smartOlt = new SmartOltFalso();
        ejecutar(genie, smartOlt, true);
        assertThat(genie.cambios).isEmpty();
        assertThat(smartOlt.desactivadas).hasSize(1);
    }

    @Test
    void sinLasDosConexionesEsperadasLaDejaSinVlan() {
        GenieFalso genie = new GenieFalso("TR069_INTERNET", "TR069_INTERNET");
        SmartOltFalso smartOlt = new SmartOltFalso();
        assertThat(ejecutar(genie, smartOlt, true).codigoFallo()).isEqualTo("FALLO_FOTO_PREVIA");
        assertThat(genie.cambios).isEmpty();
        assertThat(smartOlt.desactivadas).containsExactly("HWTCFCFC55B3");
    }

    @SuppressWarnings("unused")
    private static OltConsolePort sinConsola() {
        return null;
    }
}
