package com.guajiranet.migradoronu.ejecucion.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.guajiranet.migradoronu.comun.puertos.ActorType;
import com.guajiranet.migradoronu.ejecucion.aplicacion.CambioDePerfilUnaOnu.Contexto;
import com.guajiranet.migradoronu.ejecucion.aplicacion.CambioDePerfilUnaOnu.Final;
import com.guajiranet.migradoronu.ejecucion.aplicacion.CambioDePerfilUnaOnu.Resultado;
import com.guajiranet.migradoronu.ejecucion.puertos.salida.GestionSmartOltPort;

class CambioDeGestionUnaOnuTest {

    private static final TrabajoOnu TRABAJO = new TrabajoOnu(1, 2, "LOT-1", false, 3, "48575443FCFC55B3", "OLT_FONSECA", "0/5", 11, 55,
            "GUAJIRANET1", true, null, "VLAN_GESTION");

    /** SmartOLT de mentira: devuelve los estados en orden y anota los cambios. */
    private static final class SmartOltFalso implements GestionSmartOltPort {
        final List<EstadoGestionOnu> lecturas;
        final List<String> cambios = new ArrayList<>();
        RuntimeException alCambiar;
        int leidas;

        SmartOltFalso(EstadoGestionOnu... lecturas) {
            this.lecturas = List.of(lecturas);
        }

        @Override
        public Optional<EstadoGestionOnu> leer(String sn) {
            return Optional.of(lecturas.get(Math.min(leidas++, lecturas.size() - 1)));
        }

        @Override
        public void activarTr069(String idExterno, String perfil) {
            cambios.add(idExterno + " tr069 " + perfil);
        }

        @Override
        public void desactivarGestion(String idExterno) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void activarDhcp(String idExterno, int vlan) {
            cambios.add(idExterno + " vlan " + vlan);
            if (alCambiar != null) {
                throw alCambiar;
            }
        }
    }

    private static EstadoGestionOnu inactiva(boolean enLinea) {
        return new EstadoGestionOnu("HWTCFCFC55B3", enLinea, "Inactive", null, null);
    }

    private static final EstadoGestionOnu CON_VLAN = new EstadoGestionOnu("HWTCFCFC55B3", true, "DHCP", 46, null);
    private static final EstadoGestionOnu CON_VLAN_Y_TR069 = new EstadoGestionOnu("HWTCFCFC55B3", true, "DHCP", 46, null, true);

    private Resultado ejecutar(SmartOltFalso smartOlt, boolean real) {
        return new CambioDeGestionUnaOnu(smartOlt, entrada -> { }, d -> { })
                .ejecutar(TRABAJO, 46, real, new Contexto(ActorType.PROCESO_EJECUTOR, 3L, 2L, 1L));
    }

    @Test
    void laQueYaTieneLaVlanYElTr069NoSeToca() {
        SmartOltFalso smartOlt = new SmartOltFalso(CON_VLAN_Y_TR069);
        assertThat(ejecutar(smartOlt, true).fin()).isEqualTo(Final.YA_TENIA);
        assertThat(smartOlt.cambios).isEmpty();
    }

    @Test
    void laQueTieneLaVlanSinTr069SoloActivaElTr069() {
        SmartOltFalso smartOlt = new SmartOltFalso(CON_VLAN);
        assertThat(ejecutar(smartOlt, true).fin()).isEqualTo(Final.COMPLETADA);
        assertThat(smartOlt.cambios).containsExactly("HWTCFCFC55B3 tr069 SmartOLT");
    }

    @Test
    void laQueEstaFueraDeLineaNoSeToca() {
        SmartOltFalso smartOlt = new SmartOltFalso(inactiva(false));
        Resultado r = ejecutar(smartOlt, true);
        assertThat(r.fin()).isEqualTo(Final.EXCLUIDA);
        assertThat(r.codigoFallo()).isEqualTo("EXC_FUERA_DE_LINEA");
        assertThat(smartOlt.cambios).isEmpty();
    }

    @Test
    void laSimulacionNoMandaNada() {
        SmartOltFalso smartOlt = new SmartOltFalso(inactiva(true));
        assertThat(ejecutar(smartOlt, false).fin()).isEqualTo(Final.SIMULADA);
        assertThat(smartOlt.cambios).isEmpty();
    }

    @Test
    void cambiaUnaVezYVerificaHastaQueSmartOltLoMuestra() {
        SmartOltFalso smartOlt = new SmartOltFalso(inactiva(true), inactiva(true), CON_VLAN);
        Resultado r = ejecutar(smartOlt, true);
        assertThat(r.fin()).isEqualTo(Final.COMPLETADA);
        assertThat(smartOlt.cambios).containsExactly("HWTCFCFC55B3 vlan 46", "HWTCFCFC55B3 tr069 SmartOLT");
        assertThat(r.pasos()).extracting(CambioDePerfilUnaOnu.Paso::codigo).containsExactly("P03", "P05", "P06", "P07");
    }

    @Test
    void siSmartOltRechazaYNoQuedoEsFallidaSinReintentar() {
        SmartOltFalso smartOlt = new SmartOltFalso(inactiva(true));
        smartOlt.alCambiar = new IllegalStateException("wrong VLAN-ID");
        Resultado r = ejecutar(smartOlt, true);
        assertThat(r.fin()).isEqualTo(Final.FALLIDA);
        assertThat(r.codigoFallo()).isEqualTo("FALLO_OLT_RECHAZO");
        assertThat(smartOlt.cambios).hasSize(1);
    }

    @Test
    void siSmartOltDaErrorPeroElCambioQuedoSeDaPorHecho() {
        SmartOltFalso smartOlt = new SmartOltFalso(inactiva(true), CON_VLAN);
        smartOlt.alCambiar = new IllegalStateException("timeout");
        assertThat(ejecutar(smartOlt, true).fin()).isEqualTo(Final.COMPLETADA);
        assertThat(smartOlt.cambios).containsExactly("HWTCFCFC55B3 vlan 46", "HWTCFCFC55B3 tr069 SmartOLT");
    }
}
