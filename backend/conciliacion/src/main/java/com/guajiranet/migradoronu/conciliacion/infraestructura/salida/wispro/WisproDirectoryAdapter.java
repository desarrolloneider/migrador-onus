package com.guajiranet.migradoronu.conciliacion.infraestructura.salida.wispro;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import com.guajiranet.migradoronu.conciliacion.dominio.Subscriber;
import com.guajiranet.migradoronu.conciliacion.puertos.salida.SubscriberDirectoryPort;
import com.guajiranet.migradoronu.integraciones.wispro.WisproClient;

import tools.jackson.databind.JsonNode;

/**
 * Traduce los contratos de Wispro a Subscriber. El contrato no trae el nombre del cliente, así que también se bajan
 * los clientes. Las dos descargas se guardan 5 minutos: armar varias vistas previas seguidas no repite la descarga.
 */
public class WisproDirectoryAdapter implements SubscriberDirectoryPort {

    private static final Duration VIGENCIA = Duration.ofMinutes(5);

    private final Supplier<WisproClient> cliente;
    private final Clock reloj;
    private List<Subscriber> contratos = List.of();
    private Instant leidosEn = Instant.MIN;

    public WisproDirectoryAdapter(Supplier<WisproClient> cliente, Clock reloj) {
        this.cliente = Objects.requireNonNull(cliente, "cliente");
        this.reloj = Objects.requireNonNull(reloj, "reloj");
    }

    @Override
    public synchronized List<Subscriber> listAll() {
        Instant ahora = reloj.instant();
        if (Duration.between(leidosEn, ahora).compareTo(VIGENCIA) > 0) {
            Map<String, String> nombres = new HashMap<>();
            cliente.get().clientes().forEach(c -> {
                String id = texto(c, "id");
                String nombre = texto(c, "name");
                if (id != null && nombre != null) {
                    nombres.put(id, nombre);
                }
            });
            contratos = cliente.get().contratos().stream()
                    .filter(c -> texto(c, "public_id") != null || texto(c, "id") != null)
                    .map(c -> new Subscriber(nombres.get(texto(c, "client_id")),
                            texto(c, "public_id") != null ? texto(c, "public_id") : texto(c, "id"),
                            texto(c, "ip"), texto(c, "state")))
                    .toList();
            leidosEn = ahora;
        }
        return contratos;
    }

    @Override
    public List<Subscriber> findByIp(String ip) {
        return listAll().stream().filter(s -> ip != null && ip.equals(s.ip())).toList();
    }

    private static String texto(JsonNode r, String campo) {
        JsonNode v = r.get(campo);
        return v == null || v.isNull() || !v.isValueNode() || v.asString().isBlank() ? null : v.asString().strip();
    }
}
