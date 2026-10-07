package com.guajiranet.migradoronu.integraciones.wispro;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.guajiranet.migradoronu.integraciones.http.ClienteJson;
import com.guajiranet.migradoronu.integraciones.http.ErrorDeIntegracion;

import tools.jackson.databind.JsonNode;

/**
 * API de Wispro, solo lectura (RN-03). No permite buscar por IP, así que se descargan todos los contratos con
 * 1000 por página y se comprueba contra meta.pagination que la descarga quedó completa (RN-39).
 */
public class WisproClient {

    public static final String SISTEMA = "Wispro";
    private static final int POR_PAGINA = 1000;

    private final ClienteJson http;

    public WisproClient(String urlBase, String token, Duration tiempoMaximo) {
        if (token == null || token.isBlank()) {
            throw new ErrorDeIntegracion(SISTEMA, "No está configurado el token de Wispro (WISPRO_API_TOKEN).", null);
        }
        this.http = new ClienteJson(SISTEMA, urlBase, h -> h.set("Authorization", token), tiempoMaximo);
    }

    public List<JsonNode> contratos() {
        return todo("/contracts");
    }

    public List<JsonNode> clientes() {
        return todo("/clients");
    }

    /** Una página, para el diagnóstico. */
    public JsonNode pagina(String ruta, int pagina, int porPagina) {
        return http.get(ruta, Map.of("page", pagina, "per_page", porPagina));
    }

    private List<JsonNode> todo(String ruta) {
        List<JsonNode> registros = new ArrayList<>();
        int totalPaginas = 1;
        long totalRegistros = -1;
        for (int p = 1; p <= totalPaginas; p++) {
            JsonNode respuesta = pagina(ruta, p, POR_PAGINA);
            JsonNode paginacion = respuesta.path("meta").path("pagination");
            if (paginacion.isMissingNode()) {
                throw new ErrorDeIntegracion(SISTEMA, "Wispro no devolvió la paginación de " + ruta + ".", null);
            }
            totalPaginas = paginacion.path("total_pages").asInt(1);
            totalRegistros = paginacion.path("total_records").asLong(-1);
            respuesta.path("data").forEach(registros::add);
        }
        if (totalRegistros >= 0 && registros.size() != totalRegistros) {
            throw new ErrorDeIntegracion(SISTEMA, "La descarga de " + ruta + " quedó incompleta: llegaron " + registros.size()
                    + " de " + totalRegistros + ". Intenta de nuevo.", null);
        }
        return registros;
    }
}
