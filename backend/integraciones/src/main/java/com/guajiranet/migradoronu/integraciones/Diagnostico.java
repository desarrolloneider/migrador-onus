package com.guajiranet.migradoronu.integraciones;

import java.io.PrintStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import com.guajiranet.migradoronu.integraciones.http.ErrorDeIntegracion;
import com.guajiranet.migradoronu.integraciones.smartolt.SmartOltClient;
import com.guajiranet.migradoronu.integraciones.wispro.WisproClient;

import tools.jackson.databind.JsonNode;

/**
 * Comprueba la conexión con SmartOLT y Wispro y muestra qué campos traen sus respuestas, para confirmar PC-09 y
 * PC-32. Muestra nombres de campos y tipos, nunca valores: no imprime datos de clientes. Solo de OLT y perfiles,
 * que no son datos personales, muestra algunos valores.
 */
public final class Diagnostico {

    /**
     * Campos cuyo valor sí se muestra: identifican OLT y perfiles, no clientes. {@code name} e {@code id} no van:
     * en SmartOLT el nombre de la ONU lleva el nombre del cliente.
     */
    private static final Set<String> VISIBLES = Set.of("olt_name", "olt_id", "hardware_version", "tr069",
            "tr069_profile", "status", "board", "port", "onu_type_name", "mode", "wan_mode");

    private final PrintStream salida;
    private final UnaryOperator<String> config;

    public Diagnostico(PrintStream salida, UnaryOperator<String> config) {
        this.salida = salida;
        this.config = config;
    }

    public int ejecutar() {
        boolean bien = smartOlt() & wispro();
        salida.println();
        salida.println(bien ? "Diagnóstico terminado sin errores." : "Diagnóstico terminado con errores.");
        return bien ? 0 : 1;
    }

    private boolean smartOlt() {
        salida.println("== SmartOLT");
        try {
            SmartOltClient cliente = new SmartOltClient(config.apply("SMARTOLT_BASE_URL"), config.apply("SMARTOLT_API_TOKEN"),
                    Duration.ofSeconds(30));
            List<JsonNode> olts = cliente.olts();
            salida.println("OLT en SmartOLT: " + olts.size());
            olts.forEach(o -> salida.println("  id=" + texto(o, "id") + "  nombre=" + texto(o, "name")
                    + "  modelo=" + texto(o, "olt_hardware_version")));
            List<JsonNode> onus = cliente.pagina(Map.of(), 1, 1);
            if (onus.isEmpty()) {
                salida.println("get_all_onus_details no devolvió ONUs.");
            } else {
                salida.println("Campos de una ONU en get_all_onus_details:");
                describir(onus.getFirst(), "  ");
            }
            return true;
        } catch (ErrorDeIntegracion e) {
            salida.println("ERROR: " + e.getMessage());
            return false;
        }
    }

    private boolean wispro() {
        salida.println();
        salida.println("== Wispro");
        try {
            WisproClient cliente = new WisproClient(config.apply("WISPRO_BASE_URL"), config.apply("WISPRO_API_TOKEN"),
                    Duration.ofSeconds(30));
            for (String ruta : List.of("/contracts", "/clients")) {
                JsonNode respuesta = cliente.pagina(ruta, 1, 1);
                salida.println(ruta + ": " + respuesta.path("meta").path("pagination").path("total_records").asString("?")
                        + " registros");
                JsonNode primero = respuesta.path("data").path(0);
                if (!primero.isMissingNode()) {
                    describir(primero, "  ");
                }
            }
            return true;
        } catch (ErrorDeIntegracion e) {
            salida.println("ERROR: " + e.getMessage());
            return false;
        }
    }

    private void describir(JsonNode nodo, String sangria) {
        for (var campo : nodo.properties()) {
            JsonNode v = campo.getValue();
            String tipo = v.isNull() ? "vacío" : v.isObject() ? "objeto" : v.isArray() ? "lista(" + v.size() + ")"
                    : v.isNumber() ? "número" : v.isBoolean() ? "sí/no" : "texto";
            String valor = VISIBLES.contains(campo.getKey()) && v.isValueNode() ? " = " + v.asString() : "";
            salida.println(sangria + campo.getKey() + ": " + tipo + valor);
            if (v.isObject()) {
                describir(v, sangria + "  ");
            } else if (v.isArray() && !v.isEmpty() && v.get(0).isObject()) {
                describir(v.get(0), sangria + "  ");
            }
        }
    }

    private static String texto(JsonNode nodo, String campo) {
        JsonNode v = nodo.get(campo);
        return v == null || v.isNull() ? "-" : v.asString();
    }
}
