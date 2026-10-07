package com.guajiranet.migradoronu.conciliacion.infraestructura.salida.smartolt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.Optional;
import java.util.regex.Pattern;

import com.guajiranet.migradoronu.comun.errores.ErrorDeNegocio;
import com.guajiranet.migradoronu.conciliacion.dominio.FormatoSn;
import com.guajiranet.migradoronu.conciliacion.dominio.OnuCandidate;
import com.guajiranet.migradoronu.conciliacion.dominio.OnuPosition;
import com.guajiranet.migradoronu.conciliacion.puertos.salida.OnuInventoryPort;
import com.guajiranet.migradoronu.integraciones.smartolt.SmartOltClient;

import tools.jackson.databind.JsonNode;

/**
 * Traduce el inventario de SmartOLT a OnuCandidate (capa anticorrupción). El código de la OLT es su nombre en
 * SmartOLT (por ejemplo OLT_VILLAREINA): así se debe registrar en la tabla olts. Los campos se confirmaron con el
 * comando {@code diagnostico} el 6 de octubre de 2026.
 *
 * <p>Ojo: tr069_profile es el perfil que administra SmartOLT, no necesariamente el que tiene la ONU en la OLT
 * ({@code bound-info}). Para el cambio de perfil 2 a 3 la fuente de verdad es la OLT.
 *
 * <p>Para no gastar el cupo de 1000 llamadas por hora, la lista de OLT se guarda 10 minutos y el inventario completo
 * 3 minutos (la búsqueda por SN de la prueba individual sale de ese mismo inventario, nunca de get_onus_details_by_sn).
 */
public class SmartOltInventoryAdapter implements OnuInventoryPort {

    private static final Pattern IPV4 =
            Pattern.compile("(?<![\\d.])(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)(?![\\d.])");
    private static final Duration VIGENCIA_OLTS = Duration.ofMinutes(10);
    private static final Duration VIGENCIA_INVENTARIO = Duration.ofMinutes(3);

    private final Supplier<SmartOltClient> cliente;
    private final Clock reloj;
    private Map<String, String> oltsPorId = Map.of();
    private Instant oltsLeidasEn = Instant.MIN;
    private List<OnuCandidate> inventario = List.of();
    private Instant inventarioLeidoEn = Instant.MIN;

    public SmartOltInventoryAdapter(Supplier<SmartOltClient> cliente, Clock reloj) {
        this.cliente = Objects.requireNonNull(cliente, "cliente");
        this.reloj = Objects.requireNonNull(reloj, "reloj");
    }

    @Override
    public synchronized List<OnuCandidate> listOnus(String marca, String oltCodigo, String tarjeta, Integer pon) {
        Map<String, Object> filtros = new LinkedHashMap<>();
        if (oltCodigo != null) {
            filtros.put("olt_id", oltIdDe(oltCodigo));
            if (tarjeta != null) {
                filtros.put("board", numeroDeTarjeta(tarjeta));
                filtros.put("port", pon);
            }
        }
        List<OnuCandidate> onus = filtros.isEmpty() ? inventarioCompleto() : convertir(cliente.get().onus(filtros));
        return onus.stream().filter(o -> marca == null || marca.equalsIgnoreCase(o.marca())).toList();
    }

    @Override
    public synchronized List<OnuCandidate> findBySn(String sn) {
        String buscado = FormatoSn.normalizar(sn).orElse(sn.strip().toUpperCase(Locale.ROOT));
        return inventarioCompleto().stream()
                .filter(o -> buscado.equals(FormatoSn.normalizar(o.rawSn()).orElse(o.rawSn().toUpperCase(Locale.ROOT))))
                .toList();
    }

    private List<OnuCandidate> inventarioCompleto() {
        Instant ahora = reloj.instant();
        if (Duration.between(inventarioLeidoEn, ahora).compareTo(VIGENCIA_INVENTARIO) > 0) {
            inventario = convertir(cliente.get().onus(Map.of()));
            inventarioLeidoEn = ahora;
        }
        return inventario;
    }

    private List<OnuCandidate> convertir(List<JsonNode> registros) {
        Map<String, String> olts = olts();
        return registros.stream().map(r -> convertir(r, olts)).flatMap(Optional::stream).toList();
    }

    /** Un registro sin SN o sin posición completa no se puede usar: se descarta, nunca se completa con supuestos (RN-14). */
    static Optional<OnuCandidate> convertir(JsonNode r, Map<String, String> olts) {
        String sn = texto(r, "sn");
        String oltId = texto(r, "olt_id");
        String olt = Optional.ofNullable(texto(r, "olt_name")).orElse(oltId == null ? null : olts.getOrDefault(oltId, oltId));
        Integer board = entero(r, "board");
        Integer port = entero(r, "port");
        Integer onu = entero(r, "onu");
        if (sn == null || olt == null || board == null || port == null || onu == null) {
            return Optional.empty();
        }
        String estado = texto(r, "status");
        boolean enLinea = estado != null && estado.equalsIgnoreCase("online");
        String ip = Optional.ofNullable(texto(r, "ip_address")).orElseGet(() -> ipEnElNombre(texto(r, "name")));
        String perfil = texto(r, "tr069_profile");
        String modelo = texto(r, "onu_type_name");
        return Optional.of(new OnuCandidate(sn, marcaDe(sn, texto(r, "vendor", "brand")), modelo,
                new OnuPosition(olt, "0/" + board, port, onu), enLinea, ip, perfil));
    }

    /**
     * En modo Routing SmartOLT deja ip_address vacío; GuajiraNet escribe la IP en el nombre de la ONU con la forma
     * {@code <número> <IP> (<cliente>)}. Si el nombre trae una sola IPv4 se usa; si trae ninguna o varias, no hay IP.
     */
    static String ipEnElNombre(String nombre) {
        if (nombre == null) {
            return null;
        }
        List<String> ips = IPV4.matcher(nombre).results().map(m -> m.group()).toList();
        return ips.size() == 1 ? ips.getFirst() : null;
    }

    /** La marca sale del fabricante que traen los 4 primeros caracteres del SN (HWTC = Huawei) si SmartOLT no la da. */
    static String marcaDe(String sn, String vendor) {
        if (vendor != null) {
            return vendor.toUpperCase(Locale.ROOT).startsWith("HUAWEI") || vendor.equalsIgnoreCase("HWTC") ? "Huawei" : vendor;
        }
        String prefijo = sn.strip().toUpperCase(Locale.ROOT);
        if (prefijo.startsWith("HWTC") || prefijo.startsWith("48575443")) {
            return "Huawei";
        }
        if (prefijo.startsWith("ZTEG") || prefijo.startsWith("5A544547")) {
            return "ZTE";
        }
        return prefijo.length() >= 4 && Character.isLetter(prefijo.charAt(0)) ? prefijo.substring(0, 4) : "Desconocida";
    }

    private synchronized Map<String, String> olts() {
        Instant ahora = reloj.instant();
        if (Duration.between(oltsLeidasEn, ahora).compareTo(VIGENCIA_OLTS) > 0) {
            Map<String, String> nuevas = new HashMap<>();
            cliente.get().olts().forEach(o -> {
                String id = texto(o, "id");
                String nombre = texto(o, "name");
                if (id != null && nombre != null) {
                    nuevas.put(id, nombre);
                }
            });
            oltsPorId = nuevas;
            oltsLeidasEn = ahora;
        }
        return oltsPorId;
    }

    private String oltIdDe(String oltCodigo) {
        return olts().entrySet().stream().filter(e -> e.getValue().equalsIgnoreCase(oltCodigo)).map(Map.Entry::getKey).findFirst()
                .orElseThrow(() -> ErrorDeNegocio.noEncontrado("SmartOLT no tiene una OLT llamada " + oltCodigo + "."));
    }

    private static int numeroDeTarjeta(String tarjeta) {
        String[] partes = tarjeta.split("/");
        return Integer.parseInt(partes[partes.length - 1].strip());
    }

    private static String texto(JsonNode r, String... campos) {
        for (String campo : campos) {
            JsonNode v = r.get(campo);
            if (v != null && !v.isNull() && v.isValueNode() && !v.asString().isBlank()) {
                return v.asString().strip();
            }
        }
        return null;
    }

    private static Integer entero(JsonNode r, String... campos) {
        String t = texto(r, campos);
        try {
            return t == null ? null : Integer.valueOf(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
