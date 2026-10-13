package com.guajiranet.migradoronu.ensamblaje.consola;

import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.guajiranet.migradoronu.ejecucion.aplicacion.EstadoGestionOnu;
import com.guajiranet.migradoronu.ejecucion.puertos.salida.GestionSmartOltPort;
import com.guajiranet.migradoronu.integraciones.smartolt.SmartOltClient;

import tools.jackson.databind.JsonNode;

/** La IP de gestión de una ONU en SmartOLT, leída en el momento y cambiada por su API. */
@Component
public class GestionPorSmartOlt implements GestionSmartOltPort {

    private final String url;
    private final String token;
    private volatile SmartOltClient cliente;

    public GestionPorSmartOlt(@Value("${SMARTOLT_BASE_URL:}") String url, @Value("${SMARTOLT_API_TOKEN:}") String token) {
        this.url = url;
        this.token = token;
    }

    private SmartOltClient cliente() {
        if (cliente == null) {
            cliente = new SmartOltClient(url, token, Duration.ofSeconds(60));
        }
        return cliente;
    }

    @Override
    public Optional<EstadoGestionOnu> leer(String sn) {
        for (String forma : formas(sn)) {
            List<JsonNode> registros = cliente().onusPorSn(forma);
            Optional<JsonNode> r = registros.stream().filter(x -> forma.equalsIgnoreCase(texto(x, "sn"))).findFirst();
            if (r.isPresent()) {
                JsonNode o = r.get();
                String id = Optional.ofNullable(texto(o, "unique_external_id")).orElse(texto(o, "sn"));
                String vlan = texto(o, "mgmt_ip_vlan");
                return Optional.of(new EstadoGestionOnu(id, "online".equalsIgnoreCase(texto(o, "status")), texto(o, "mgmt_ip_mode"),
                        vlan == null ? null : Integer.valueOf(vlan), texto(o, "mgmt_ip_address")));
            }
        }
        return Optional.empty();
    }

    @Override
    public void activarDhcp(String idExterno, int vlan) {
        cliente().activarGestionDhcp(idExterno, vlan);
    }

    /** 4857544347ADC1B3 se busca como HWTC47ADC1B3 (así lo escribe SmartOLT) y tal cual. */
    static List<String> formas(String sn) {
        String limpio = sn.strip().toUpperCase(Locale.ROOT);
        if (limpio.matches("[0-9A-F]{16}")) {
            String fabricante = new String(HexFormat.of().parseHex(limpio.substring(0, 8)), java.nio.charset.StandardCharsets.US_ASCII);
            if (fabricante.matches("[A-Z0-9]{4}")) {
                return List.of(fabricante + limpio.substring(8), limpio);
            }
        }
        return List.of(limpio);
    }

    private static String texto(JsonNode r, String campo) {
        JsonNode v = r.get(campo);
        return v == null || v.isNull() || !v.isValueNode() || v.asString().isBlank() ? null : v.asString().strip();
    }
}
