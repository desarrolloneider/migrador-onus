package com.guajiranet.migradoronu.integraciones.http;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * GET con JSON sobre RestClient, con tiempo máximo por petición y una espera si el servicio responde 429 con
 * Retry-After. No sabe nada de ONUs ni de contratos.
 */
public final class ClienteJson {

    private static final int MAXIMO_ESPERAS_429 = 3;
    private static final Duration ESPERA_MAXIMA_429 = Duration.ofSeconds(120);

    private final String sistema;
    private final RestClient rest;
    private final JsonMapper json = JsonMapper.builder().build();

    public ClienteJson(String sistema, String urlBase, Consumer<HttpHeaders> cabeceras, Duration tiempoMaximo) {
        this.sistema = Objects.requireNonNull(sistema, "sistema");
        if (urlBase == null || urlBase.isBlank()) {
            throw new ErrorDeIntegracion(sistema, "No está configurada la URL de " + sistema + ".", null);
        }
        HttpClient http = HttpClient.newBuilder().connectTimeout(tiempoMaximo).followRedirects(HttpClient.Redirect.NORMAL).build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(tiempoMaximo);
        this.rest = RestClient.builder()
                .baseUrl(urlBase.endsWith("/") ? urlBase.substring(0, urlBase.length() - 1) : urlBase)
                .requestFactory(fabrica)
                .defaultHeaders(h -> {
                    h.set(HttpHeaders.ACCEPT, "application/json");
                    cabeceras.accept(h);
                })
                .build();
    }

    public JsonNode get(String ruta, Map<String, ?> parametros) {
        for (int intento = 0; ; intento++) {
            try {
                String cuerpo = rest.get().uri(b -> construir(b, ruta, parametros)).retrieve().body(String.class);
                return json.readTree(cuerpo == null ? "null" : cuerpo);
            } catch (HttpStatusCodeException e) {
                HttpStatusCode estado = e.getStatusCode();
                if (estado.value() == 429 && intento < MAXIMO_ESPERAS_429) {
                    esperar(retryAfter(e.getResponseHeaders()));
                    continue;
                }
                throw new ErrorDeIntegracion(sistema, mensaje(estado.value()), e);
            } catch (ResourceAccessException e) {
                throw new ErrorDeIntegracion(sistema, sistema + " no responde.", e);
            } catch (RuntimeException e) {
                if (e instanceof ErrorDeIntegracion) {
                    throw e;
                }
                throw new ErrorDeIntegracion(sistema, sistema + " devolvió una respuesta que no se pudo leer.", e);
            }
        }
    }

    private static URI construir(UriBuilder b, String ruta, Map<String, ?> parametros) {
        b.path(ruta);
        parametros.forEach((k, v) -> {
            if (v != null) {
                b.queryParam(k, v);
            }
        });
        return b.build();
    }

    private String mensaje(int estado) {
        return switch (estado) {
            case 401, 403 -> sistema + " rechazó el token de acceso (" + estado + "). Revisa el token en el archivo .env.";
            case 404 -> sistema + " no encontró la ruta pedida (404). Revisa la URL base.";
            case 429 -> sistema + " limitó las consultas (429). Intenta en unos minutos.";
            default -> sistema + " respondió con error " + estado + ".";
        };
    }

    private static Duration retryAfter(HttpHeaders cabeceras) {
        String valor = cabeceras == null ? null : cabeceras.getFirst(HttpHeaders.RETRY_AFTER);
        try {
            Duration d = Duration.ofSeconds(Long.parseLong(valor == null ? "30" : valor.strip()));
            return d.compareTo(ESPERA_MAXIMA_429) > 0 ? ESPERA_MAXIMA_429 : d;
        } catch (NumberFormatException e) {
            return Duration.ofSeconds(30);
        }
    }

    private static void esperar(Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrumpido mientras se esperaba el Retry-After", e);
        }
    }
}
