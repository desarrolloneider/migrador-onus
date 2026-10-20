package com.guajiranet.migradoronu.conciliacion.infraestructura.salida.smartolt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class SmartOltInventoryAdapterTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static JsonNode registro(String json) {
        return JSON.readTree(json);
    }

    /** SmartOLT de mentira: cuenta las descargas del inventario (get_all_onus_details). */
    private static final class SmartOltFalso extends com.guajiranet.migradoronu.integraciones.smartolt.SmartOltClient {
        int descargas;

        SmartOltFalso() {
            super();
        }

        @Override
        public java.util.List<JsonNode> olts() {
            return java.util.List.of(registro("{\"id\":\"8\",\"name\":\"OLT_VILLAREINA\"}"));
        }

        @Override
        public java.util.List<JsonNode> onus(Map<String, ?> filtros) {
            descargas++;
            return java.util.List.of(registro("""
                    {"sn":"HWTC1A2B3C4D","olt_id":"8","olt_name":"OLT_VILLAREINA","board":"0","port":"1","onu":"12",
                     "onu_type_name":"EG8141A5","name":"993 10.7.0.35","status":"Online"}
                    """));
        }
    }

    /** Con la precarga, "Estado de OLT", cada OLT y las vistas previas salen de la misma copia: una sola descarga. */
    @Test
    void conLaPrecargaLasPantallasNoGastanOtraDescarga() {
        SmartOltFalso smartOlt = new SmartOltFalso();
        SmartOltInventoryAdapter adaptador = new SmartOltInventoryAdapter(() -> smartOlt, java.time.Clock.systemUTC());
        assertThat(adaptador.refrescarInventario()).isTrue();
        assertThat(adaptador.listOnus("Huawei", "OLT_VILLAREINA", null, null)).hasSize(1);
        assertThat(adaptador.listOnus(null, null, null, null)).hasSize(1);
        assertThat(adaptador.listOnus("Huawei", "OLT_VILLAREINA", "0/0", 1)).hasSize(1);
        assertThat(smartOlt.descargas).isEqualTo(1);
    }

    /** Nunca más de 10 descargas por hora (SmartOLT permite 15 y bloquea la IP al pasarse). */
    @Test
    void laPrecargaRespetaElCupoPorHora() {
        SmartOltFalso smartOlt = new SmartOltFalso();
        SmartOltInventoryAdapter adaptador = new SmartOltInventoryAdapter(() -> smartOlt, java.time.Clock.systemUTC());
        for (int i = 0; i < 12; i++) {
            adaptador.refrescarInventario();
        }
        assertThat(smartOlt.descargas).isEqualTo(SmartOltInventoryAdapter.MAXIMO_INVENTARIOS_POR_HORA);
        assertThat(adaptador.refrescarInventario()).isFalse();
    }

    @Test
    void convierteUnRegistroRealDeSmartOlt() {
        var onu = SmartOltInventoryAdapter.convertir(registro("""
                {"sn":"HWTC1A2B3C4D","olt_id":"8","olt_name":"OLT_VILLAREINA","board":"0","port":"1","onu":"12",
                 "onu_type_name":"EG8141A5","name":"993 10.7.0.35 (Cliente de prueba)","ip_address":null,
                 "tr069_profile":"","status":"Online"}
                """), Map.of()).orElseThrow();
        assertThat(onu.position().oltCodigo()).isEqualTo("OLT_VILLAREINA");
        assertThat(onu.position().tarjeta()).isEqualTo("0/0");
        assertThat(onu.position().pon()).isEqualTo(1);
        assertThat(onu.position().onuId()).isEqualTo(12);
        assertThat(onu.ip()).isEqualTo("10.7.0.35");
        assertThat(onu.enLinea()).isTrue();
        assertThat(onu.marca()).isEqualTo("Huawei");
        assertThat(onu.perfilTr069()).isNull();
    }

    @Test
    void laIpDeSmartOltGanaSobreLaDelNombre() {
        var onu = SmartOltInventoryAdapter.convertir(registro("""
                {"sn":"HWTC1A2B3C4D","olt_name":"X","board":"0","port":"1","onu":"2","ip_address":"10.9.9.9",
                 "name":"1 10.7.0.35 (A)","status":"Offline"}
                """), Map.of()).orElseThrow();
        assertThat(onu.ip()).isEqualTo("10.9.9.9");
        assertThat(onu.enLinea()).isFalse();
    }

    @Test
    void sinPosicionCompletaNoSeUsa() {
        assertThat(SmartOltInventoryAdapter.convertir(registro("""
                {"sn":"HWTC1A2B3C4D","olt_name":"X","board":"0","onu":"2"}
                """), Map.of())).isEmpty();
    }

    @Test
    void ipEnElNombre() {
        assertThat(SmartOltInventoryAdapter.ipEnElNombre("993 10.7.0.35 (Ana)")).isEqualTo("10.7.0.35");
        assertThat(SmartOltInventoryAdapter.ipEnElNombre("sin ip")).isNull();
        assertThat(SmartOltInventoryAdapter.ipEnElNombre("10.0.0.1 y 10.0.0.2")).isNull();
        assertThat(SmartOltInventoryAdapter.ipEnElNombre("v1.2.3.4.5")).isNull();
    }
}
