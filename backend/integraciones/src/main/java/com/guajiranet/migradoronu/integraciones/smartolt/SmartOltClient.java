package com.guajiranet.migradoronu.integraciones.smartolt;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.guajiranet.migradoronu.integraciones.http.ClienteJson;
import com.guajiranet.migradoronu.integraciones.http.ErrorDeIntegracion;

import tools.jackson.databind.JsonNode;

/**
 * API de SmartOLT, solo lectura (RN-03). Usa el inventario paginado {@code get_all_onus_details} (página de 100), que
 * no consume el cupo de 15 exportaciones por hora. Nunca consulta ONU por ONU: la documentación lo prohíbe.
 */
public class SmartOltClient {

    public static final String SISTEMA = "SmartOLT";
    private static final int TAMANO_PAGINA = 100;
    private static final int MAXIMO_PAGINAS = 500;

    private final ClienteJson http;

    public SmartOltClient(String urlBase, String token, Duration tiempoMaximo) {
        if (token == null || token.isBlank()) {
            throw new ErrorDeIntegracion(SISTEMA, "No está configurado el token de SmartOLT (SMARTOLT_API_TOKEN).", null);
        }
        this.http = new ClienteJson(SISTEMA, urlBase, h -> h.set("X-Token", token), tiempoMaximo);
    }

    /** Las OLT registradas en SmartOLT, con su ID. */
    public List<JsonNode> olts() {
        return registros(http.get("/api/system/get_olts", Map.of()));
    }

    /**
     * Todas las ONUs que cumplen los filtros (olt_id, board, port...), página por página hasta una incompleta.
     * Los filtros en null no se envían.
     */
    public List<JsonNode> onus(Map<String, ?> filtros) {
        // Sin page/page_size SmartOLT devuelve todo en UNA respuesta (11 829 ONUs, 15 de octubre). Paginando de a 100 (su
        // máximo) eran ~119 peticiones por inventario, y SmartOLT bloqueó la IP por exceso de peticiones.
        JsonNode completa = http.get("/api/onu/get_all_onus_details", filtros);
        if (completa != null && !completa.has("page")) {
            return registros(completa);
        }
        List<JsonNode> todas = new ArrayList<>();
        for (int pagina = 1; pagina <= MAXIMO_PAGINAS; pagina++) {
            List<JsonNode> registros = pagina(filtros, pagina, TAMANO_PAGINA);
            todas.addAll(registros);
            if (registros.size() < TAMANO_PAGINA) {
                return todas;
            }
        }
        throw new ErrorDeIntegracion(SISTEMA, "SmartOLT devolvió más de " + MAXIMO_PAGINAS + " páginas; se detuvo la descarga.", null);
    }

    /**
     * Las ONUs con ese SN: GET /api/onu/get_onus_details_by_sn/{sn}. Cuenta en el presupuesto general (1000 por hora),
     * no en el de get_all_onus_details (15 por hora), que SmartOLT bloquea al pasarse.
     */
    public List<JsonNode> onusPorSn(String sn) {
        return registros(http.get("/api/onu/get_onus_details_by_sn/" + java.net.URLEncoder.encode(sn, java.nio.charset.StandardCharsets.UTF_8),
                Map.of()));
    }

    /**
     * IP de gestión por DHCP en una VLAN (pantalla "Actualizar IP de gestión" → DHCP + VLAN-ID de gestión). Es el único
     * cambio que hace este cliente: POST /api/onu/set_onu_mgmt_ip_dhcp/{id externo} con vlan.
     */
    public void activarGestionDhcp(String idExterno, int vlan) {
        JsonNode r = http.postFormulario("/api/onu/set_onu_mgmt_ip_dhcp/" + java.net.URLEncoder.encode(idExterno, java.nio.charset.StandardCharsets.UTF_8),
                Map.of("vlan", String.valueOf(vlan)));
        JsonNode estado = r == null ? null : r.get("status");
        if (estado == null || !estado.asBoolean()) {
            JsonNode error = r == null ? null : (r.has("error") ? r.get("error") : r.get("response"));
            throw new ErrorDeIntegracion(SISTEMA, "SmartOLT no aplicó la IP de gestión: " + (error == null ? "sin detalle" : error.asString()), null);
        }
    }

    /** TR-069 por la IP de gestión con ese perfil: POST /api/onu/enable_tr069/{id externo} con tr069_profile y tr069_interface=mgmt. */
    public void activarTr069(String idExterno, String perfil) {
        JsonNode r = http.postFormulario("/api/onu/enable_tr069/" + java.net.URLEncoder.encode(idExterno, java.nio.charset.StandardCharsets.UTF_8),
                Map.of("tr069_profile", perfil, "tr069_interface", "mgmt"));
        JsonNode estado = r == null ? null : r.get("status");
        if (estado == null || !estado.asBoolean()) {
            JsonNode error = r == null ? null : (r.has("error") ? r.get("error") : r.get("response"));
            throw new ErrorDeIntegracion(SISTEMA, "SmartOLT no activó el TR-069: " + (error == null ? "sin detalle" : error.asString()), null);
        }
    }

    /** IP de gestión en Inactiva: POST /api/onu/set_onu_mgmt_ip_inactive/{id externo}. */
    public void desactivarGestion(String idExterno) {
        JsonNode r = http.postFormulario("/api/onu/set_onu_mgmt_ip_inactive/" + java.net.URLEncoder.encode(idExterno, java.nio.charset.StandardCharsets.UTF_8),
                Map.of());
        JsonNode estado = r == null ? null : r.get("status");
        if (estado == null || !estado.asBoolean()) {
            JsonNode error = r == null ? null : (r.has("error") ? r.get("error") : r.get("response"));
            throw new ErrorDeIntegracion(SISTEMA, "SmartOLT no quitó la IP de gestión: " + (error == null ? "sin detalle" : error.asString()), null);
        }
    }

    /** Una página del inventario. Sirve también para el diagnóstico con tamaño 1. */
    public List<JsonNode> pagina(Map<String, ?> filtros, int pagina, int tamano) {
        Map<String, Object> parametros = new LinkedHashMap<>(filtros);
        parametros.put("page", pagina);
        parametros.put("page_size", tamano);
        return registros(http.get("/api/onu/get_all_onus_details", parametros));
    }

    /**
     * SmartOLT responde {"status": true, "...": [registros]}. Se toma el primer arreglo de la respuesta, o la
     * respuesta misma si es un arreglo.
     */
    static List<JsonNode> registros(JsonNode respuesta) {
        if (respuesta == null || respuesta.isNull()) {
            return List.of();
        }
        if (respuesta.isArray()) {
            return lista(respuesta);
        }
        JsonNode estado = respuesta.get("status");
        if (estado != null && estado.isBoolean() && !estado.asBoolean()) {
            JsonNode error = respuesta.has("error") ? respuesta.get("error") : respuesta.get("error_code");
            throw new ErrorDeIntegracion(SISTEMA, "SmartOLT rechazó la consulta: " + (error == null ? "sin detalle" : error.asString()), null);
        }
        for (var campo : respuesta.properties()) {
            if (campo.getValue().isArray()) {
                return lista(campo.getValue());
            }
        }
        return List.of();
    }

    private static List<JsonNode> lista(JsonNode arreglo) {
        List<JsonNode> salida = new ArrayList<>(arreglo.size());
        arreglo.forEach(salida::add);
        return salida;
    }
}
