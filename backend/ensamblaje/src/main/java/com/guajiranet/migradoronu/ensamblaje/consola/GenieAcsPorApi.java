package com.guajiranet.migradoronu.ensamblaje.consola;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.guajiranet.migradoronu.ejecucion.aplicacion.EstadoServicioWan;
import com.guajiranet.migradoronu.ejecucion.puertos.salida.GenieAcsPort;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * GenieACS por la API de su interfaz web (gestion.acs.guajiranet.com/api), con el usuario del .env (GENIEACS_URL,
 * GENIEACS_USUARIO, GENIEACS_CLAVE). Inicia sesión una vez y vuelve a iniciarla si la sesión vence.
 */
@Component
public class GenieAcsPorApi implements GenieAcsPort, com.guajiranet.migradoronu.conciliacion.puertos.salida.UltimoReporteAcsPort {

    private static final String WAN = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final String url;
    private final String usuario;
    private final String clave;
    private final HttpClient http;

    public GenieAcsPorApi(@Value("${GENIEACS_URL:}") String url, @Value("${GENIEACS_USUARIO:}") String usuario,
                          @Value("${GENIEACS_CLAVE:}") String clave) {
        this.url = url == null ? "" : url.replaceAll("/+$", "");
        this.usuario = usuario;
        this.clave = clave;
        this.http = HttpClient.newBuilder().cookieHandler(new CookieManager()).connectTimeout(Duration.ofSeconds(15)).build();
    }

    @Override
    public Optional<EstadoServicioWan> leer(String sn) {
        String filtro = URLEncoder.encode("DeviceID.SerialNumber = \"" + sn + "\"", StandardCharsets.UTF_8);
        JsonNode lista = get("/api/devices/?filter=" + filtro + "&limit=1&projection=DeviceID.ID,Events.Inform,"
                + "InternetGatewayDevice.ManagementServer.ConnectionRequestURL," + WAN);
        if (lista == null || !lista.isArray() || lista.isEmpty()) {
            return Optional.empty();
        }
        JsonNode d = lista.get(0);
        Map<String, String> servicios = new LinkedHashMap<>();
        for (var campo : d.properties()) {
            if (campo.getKey().endsWith(".X_HW_SERVICELIST")) {
                String v = valor(campo.getValue());
                if (v != null) {
                    servicios.put(campo.getKey(), v);
                }
            }
        }
        if (servicios.isEmpty()) {
            servicios.putAll(deducir(d));
        }
        String inform = valor(d.get("Events.Inform"));
        return Optional.of(new EstadoServicioWan(valor(d.get("DeviceID.ID")), inform == null ? 0 : Long.parseLong(inform), servicios));
    }

    /**
     * GenieACS a veces no guarda el valor de X_HW_SERVICELIST (el parámetro existe pero vacío) aunque la ONU se reporte.
     * Entonces se deduce de por dónde contesta el TR-069 (la IP de ConnectionRequestURL): por la conexión 2 (IP de
     * gestión) es INTERNET/TR069; por la conexión 1 (IP de servicio) ya es TR069_INTERNET/OTHER. Si no cuadra, nada.
     */
    static Map<String, String> deducir(JsonNode d) {
        String c1 = WAN + ".1.WANIPConnection.1.";
        String c2 = WAN + ".2.WANIPConnection.1.";
        String ip1 = valor(d.get(c1 + "ExternalIPAddress"));
        String ip2 = valor(d.get(c2 + "ExternalIPAddress"));
        String url = valor(d.get("InternetGatewayDevice.ManagementServer.ConnectionRequestURL"));
        if (url == null || ip1 == null || ip2 == null || ip1.equals(ip2) || !d.has(c1 + "X_HW_SERVICELIST") || !d.has(c2 + "X_HW_SERVICELIST")) {
            return Map.of();
        }
        String host = url.replaceFirst("^[a-z]+://", "").replaceFirst("[:/].*$", "");
        Map<String, String> r = new LinkedHashMap<>();
        if (host.equals(ip2)) {
            r.put(c1 + "X_HW_SERVICELIST", "INTERNET");
            r.put(c2 + "X_HW_SERVICELIST", "TR069");
        } else if (host.equals(ip1)) {
            r.put(c1 + "X_HW_SERVICELIST", "TR069_INTERNET");
            r.put(c2 + "X_HW_SERVICELIST", "OTHER");
        }
        return r;
    }

    @Override
    public Optional<java.time.Instant> ultimoReporte(String sn) {
        if (url.isBlank()) {
            throw new IllegalStateException("No está configurado GenieACS (GENIEACS_URL en el .env).");
        }
        String filtro = URLEncoder.encode("DeviceID.SerialNumber = \"" + sn + "\"", StandardCharsets.UTF_8);
        JsonNode lista = get("/api/devices/?filter=" + filtro + "&limit=1&projection=Events.Inform");
        if (lista == null || !lista.isArray() || lista.isEmpty()) {
            return Optional.empty();
        }
        String inform = valor(lista.get(0).get("Events.Inform"));
        return inform == null ? Optional.empty() : Optional.of(java.time.Instant.ofEpochMilli(Long.parseLong(inform)));
    }

    private volatile java.util.Set<String> listos = java.util.Set.of();
    private volatile long listosLeidosEn;

    /** Una consulta: las ONUs con TR069_INTERNET en alguna conexión. Se guarda 2 minutos. */
    @Override
    public java.util.Set<String> conServicioWanListo() {
        if (System.currentTimeMillis() - listosLeidosEn < 120_000) {
            return listos;
        }
        java.util.Set<String> r = new java.util.HashSet<>();
        for (int c = 1; c <= 2; c++) {
            String filtro = URLEncoder.encode(WAN + "." + c + ".WANIPConnection.1.X_HW_SERVICELIST = \"TR069_INTERNET\"", StandardCharsets.UTF_8);
            JsonNode lista = get("/api/devices/?filter=" + filtro + "&projection=DeviceID.SerialNumber");
            if (lista != null && lista.isArray()) {
                lista.forEach(d -> {
                    String sn = valor(d.get("DeviceID.SerialNumber"));
                    if (sn != null) {
                        r.add(sn.toUpperCase(java.util.Locale.ROOT));
                    }
                });
            }
        }
        listos = java.util.Set.copyOf(r);
        listosLeidosEn = System.currentTimeMillis();
        return listos;
    }

    @Override
    public boolean refrescar(String idDispositivo, List<String> parametros) {
        List<Object> tarea = List.of(Map.of("name", "getParameterValues", "parameterNames", parametros));
        return tarea(idDispositivo, tarea);
    }

    @Override
    public boolean cambiar(String idDispositivo, Map<String, String> valores) {
        List<List<String>> lista = new ArrayList<>();
        valores.forEach((k, v) -> lista.add(List.of(k, v, "xsd:string")));
        return tarea(idDispositivo, List.of(Map.of("name", "setParameterValues", "parameterValues", lista)));
    }

    private boolean tarea(String idDispositivo, Object cuerpo) {
        String ruta = "/api/devices/" + URLEncoder.encode(idDispositivo, StandardCharsets.UTF_8).replace("+", "%20") + "/tasks?connection_request";
        JsonNode r = enviar("POST", ruta, JSON.writeValueAsString(cuerpo), Duration.ofSeconds(60));
        return r != null && r.isArray() && !r.isEmpty() && "done".equals(r.get(0).path("status").asString());
    }

    private JsonNode get(String ruta) {
        return enviar("GET", ruta, null, Duration.ofSeconds(30));
    }

    private JsonNode enviar(String metodo, String ruta, String cuerpo, Duration tiempo) {
        if (url.isBlank()) {
            throw new IllegalStateException("No está configurado GenieACS (GENIEACS_URL en el .env).");
        }
        for (int intento = 0; intento < 2; intento++) {
            try {
                HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url + ruta)).timeout(tiempo).header("Accept", "application/json");
                b = cuerpo == null ? b.GET() : b.header("Content-Type", "application/json").method(metodo, HttpRequest.BodyPublishers.ofString(cuerpo));
                HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
                if ((r.statusCode() == 401 || r.statusCode() == 403) && intento == 0) {
                    iniciarSesion();
                    continue;
                }
                if (r.statusCode() >= 400) {
                    throw new IllegalStateException("GenieACS respondió " + r.statusCode() + " en " + ruta.replaceAll("\\?.*", ""));
                }
                return r.body() == null || r.body().isBlank() ? null : JSON.readTree(r.body());
            } catch (IOException e) {
                throw new IllegalStateException("GenieACS no respondió: " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrumpido esperando a GenieACS", e);
            }
        }
        throw new IllegalStateException("GenieACS rechazó el usuario del .env (GENIEACS_USUARIO).");
    }

    private synchronized void iniciarSesion() throws IOException, InterruptedException {
        String cuerpo = JSON.writeValueAsString(Map.of("username", usuario == null ? "" : usuario, "password", clave == null ? "" : clave));
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url + "/login")).timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(cuerpo)).build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() >= 400) {
            throw new IllegalStateException("GenieACS rechazó el usuario del .env (GENIEACS_USUARIO).");
        }
    }

    private static String valor(JsonNode campo) {
        if (campo == null) {
            return null;
        }
        JsonNode v = campo.path("value");
        if (v.isArray() && !v.isEmpty() && !v.get(0).isNull()) {
            return v.get(0).asString();
        }
        return null;
    }
}
