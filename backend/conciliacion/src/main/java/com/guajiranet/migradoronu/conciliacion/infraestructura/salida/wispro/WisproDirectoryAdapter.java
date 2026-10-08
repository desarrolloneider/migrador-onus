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
 * los clientes (unos 26 000 registros en total, cerca de 2 minutos).
 *
 * <p>Se guarda una copia por 30 minutos que la web renueva en segundo plano ({@link #refrescar()}), para que armar
 * una vista previa no espere la descarga. No es riesgoso: antes de tocar cada ONU el ejecutor la vuelve a leer en la
 * OLT y compara SN y perfil.
 */
public class WisproDirectoryAdapter implements SubscriberDirectoryPort {

    private static final Duration VIGENCIA = Duration.ofMinutes(30);

    private record Copia(List<Subscriber> contratos, Instant leidaEn) {
    }

    private final Supplier<WisproClient> cliente;
    private final Clock reloj;
    private volatile Copia copia;
    private final Object descarga = new Object();

    public WisproDirectoryAdapter(Supplier<WisproClient> cliente, Clock reloj) {
        this.cliente = Objects.requireNonNull(cliente, "cliente");
        this.reloj = Objects.requireNonNull(reloj, "reloj");
    }

    @Override
    public List<Subscriber> listAll() {
        Copia actual = copia;
        if (actual != null && Duration.between(actual.leidaEn(), reloj.instant()).compareTo(VIGENCIA) < 0) {
            return actual.contratos();
        }
        synchronized (descarga) {
            // Otro hilo pudo descargarla mientras se esperaba.
            actual = copia;
            if (actual != null && Duration.between(actual.leidaEn(), reloj.instant()).compareTo(VIGENCIA) < 0) {
                return actual.contratos();
            }
            return descargar().contratos();
        }
    }

    /** Descarga una copia nueva y la deja lista. Si falla, la copia anterior sigue sirviendo mientras esté vigente. */
    public void refrescar() {
        synchronized (descarga) {
            descargar();
        }
    }

    @Override
    public List<Subscriber> findByIp(String ip) {
        return listAll().stream().filter(s -> ip != null && ip.equals(s.ip())).toList();
    }

    private Copia descargar() {
        Map<String, String> nombres = new HashMap<>();
        cliente.get().clientes().forEach(c -> {
            String id = texto(c, "id");
            String nombre = texto(c, "name");
            if (id != null && nombre != null) {
                nombres.put(id, nombre);
            }
        });
        List<Subscriber> contratos = cliente.get().contratos().stream()
                .filter(c -> texto(c, "public_id") != null || texto(c, "id") != null)
                .map(c -> new Subscriber(nombres.get(texto(c, "client_id")),
                        texto(c, "public_id") != null ? texto(c, "public_id") : texto(c, "id"),
                        texto(c, "ip"), texto(c, "state")))
                .toList();
        Copia nueva = new Copia(contratos, reloj.instant());
        copia = nueva;
        return nueva;
    }

    private static String texto(JsonNode r, String campo) {
        JsonNode v = r.get(campo);
        return v == null || v.isNull() || !v.isValueNode() || v.asString().isBlank() ? null : v.asString().strip();
    }
}
