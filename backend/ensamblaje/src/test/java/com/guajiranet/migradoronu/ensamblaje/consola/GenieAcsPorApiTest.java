package com.guajiranet.migradoronu.ensamblaje.consola;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class GenieAcsPorApiTest {

    private static final String W = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.";
    private static final String URL = "InternetGatewayDevice.ManagementServer.ConnectionRequestURL";

    private static JsonNode equipo(String json) {
        return JsonMapper.builder().build().readTree(json.replace('\'', '"'));
    }

    /** 7C3B04AD (EG8145V5), 16 de octubre: GenieACS sin X_HW_SERVICELIST, pero se reportaba por su IP de internet. */
    @Test
    void laQueSeReportaPorSuIpDeInternetYaTieneElTr069PorInternet() {
        JsonNode d = equipo("{'" + URL + "':{'value':['http://10.41.2.42:7547/']},"
                + "'" + W + "1.WANIPConnection.1.ExternalIPAddress':{'value':['10.41.2.42']},"
                + "'" + W + "1.WANIPConnection.1.X_HW_SERVICELIST':{}}");
        assertThat(GenieAcsPorApi.reportaPorInternet(d)).isEqualTo("10.41.2.42");
    }

    /** 3DEE02AE, pendiente: se reporta por la IP de gestión (192.168.40.x), aunque GenieACS no tenga leído su servicio. */
    @Test
    void laQueSeReportaPorLaIpDeGestionNo() {
        JsonNode d = equipo("{'" + URL + "':{'value':['http://192.168.40.16:7547/']},"
                + "'" + W + "1.WANIPConnection.1.ExternalIPAddress':{'value':['10.41.4.179']},"
                + "'" + W + "2.WANIPConnection.1.ExternalIPAddress':{'value':['192.168.40.16']},"
                + "'" + W + "2.WANIPConnection.1.X_HW_SERVICELIST':{}}");
        assertThat(GenieAcsPorApi.reportaPorInternet(d)).isNull();
    }

    /** Con INTERNET solo en esa conexión el TR-069 no puede ir por ahí: el dato está viejo y no se adivina. */
    @Test
    void conInternetSoloNoSeAdivina() {
        JsonNode d = equipo("{'" + URL + "':{'value':['http://10.41.4.159:7547/']},"
                + "'" + W + "1.WANIPConnection.1.ExternalIPAddress':{'value':['10.41.4.159']},"
                + "'" + W + "1.WANIPConnection.1.X_HW_SERVICELIST':{'value':['INTERNET']}}");
        assertThat(GenieAcsPorApi.reportaPorInternet(d)).isNull();
    }

    @Test
    void conTr069InternetSi() {
        JsonNode d = equipo("{'" + URL + "':{'value':['http://10.41.4.159:7547/']},"
                + "'" + W + "1.WANIPConnection.1.ExternalIPAddress':{'value':['10.41.4.159']},"
                + "'" + W + "1.WANIPConnection.1.X_HW_SERVICELIST':{'value':['TR069_INTERNET']}}");
        assertThat(GenieAcsPorApi.reportaPorInternet(d)).isEqualTo("10.41.4.159");
    }
}
