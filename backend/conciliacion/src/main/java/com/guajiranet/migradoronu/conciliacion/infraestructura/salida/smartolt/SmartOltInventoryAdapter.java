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
    /**
     * Copia del inventario completo y de cada OLT. La renueva la precarga cada 15 minutos (PrecargaDeSmartOlt); 20 minutos
     * de vigencia dan margen para que nunca haga falta otra descarga mientras la precarga funcione. Cada descarga es una
     * petición pesada a SmartOLT (bloqueó la IP el 15 de octubre).
     */
    private static final Duration VIGENCIA_INVENTARIO = Duration.ofMinutes(20);

    private final Supplier<SmartOltClient> cliente;
    private final Clock reloj;
    private Map<String, String> oltsPorId = Map.of();
    private Instant oltsLeidasEn = Instant.MIN;
    private List<OnuCandidate> inventario = List.of();
    private Instant inventarioLeidoEn = Instant.MIN;
    /**
     * SmartOLT permite 15 llamadas por hora a get_all_onus_details (y bloquea al pasarse). Se usan como mucho 10: con
     * eso, si ya se gastaron, se sirve la copia que haya aunque tenga más de 10 minutos.
     */
    static final int MAXIMO_INVENTARIOS_POR_HORA = 10;
    private final java.util.ArrayDeque<Instant> descargas = new java.util.ArrayDeque<>();

    public SmartOltInventoryAdapter(Supplier<SmartOltClient> cliente, Clock reloj) {
        this.cliente = Objects.requireNonNull(cliente, "cliente");
        this.reloj = Objects.requireNonNull(reloj, "reloj");
    }

    @Override
    public synchronized List<OnuCandidate> listOnus(String marca, String oltCodigo, String tarjeta, Integer pon) {
        // Con OLT: solo esa OLT (una llamada de ~1 s, con copia de 10 min por OLT). El inventario completo de toda la red
        // tarda tanto que el servidor web cortaba la pantalla (504). Las dos cuentan en el mismo cupo de 15 por hora.
        // Tarjeta, puerto y marca se filtran aquí.
        boolean todas = marca == null || marca.isBlank() || marca.equalsIgnoreCase("TODAS");
        return (oltCodigo == null ? inventarioCompleto() : deLaOlt(oltCodigo)).stream()
                .filter(o -> oltCodigo == null || oltCodigo.equalsIgnoreCase(o.position().oltCodigo()))
                .filter(o -> tarjeta == null || tarjeta.equals(o.position().tarjeta()))
                .filter(o -> pon == null || pon == o.position().pon())
                .filter(o -> todas || marca.equalsIgnoreCase(o.marca()))
                .toList();
    }

    @Override
    public List<OnuCandidate> findBySn(String sn) {
        String buscado = FormatoSn.normalizar(sn).orElse(sn.strip().toUpperCase(Locale.ROOT));
        java.util.function.Predicate<OnuCandidate> mismoSn =
                o -> buscado.equals(FormatoSn.normalizar(o.rawSn()).orElse(o.rawSn().toUpperCase(Locale.ROOT)));
        // En vivo y por SN (get_onus_details_by_sn, presupuesto general): el estado de gestión cambia entre pasos y la
        // copia del inventario puede tener minutos. Se pregunta con el SN como lo escribe SmartOLT (HWTC47ADC1B3) y en
        // hexadecimal. Nunca se baja el inventario completo para buscar una ONU.
        for (String forma : formasDeConsulta(buscado)) {
            List<OnuCandidate> encontradas = convertir(cliente.get().onusPorSn(forma)).stream().filter(mismoSn).toList();
            if (!encontradas.isEmpty()) {
                return encontradas;
            }
        }
        return List.of();
    }

    /** {@code 4857544347ADC1B3} se consulta como {@code HWTC47ADC1B3} y como {@code 4857544347ADC1B3}. */
    static List<String> formasDeConsulta(String snComun) {
        if (snComun.matches("[0-9A-F]{16}")) {
            String fabricante = new String(java.util.HexFormat.of().parseHex(snComun.substring(0, 8)), java.nio.charset.StandardCharsets.US_ASCII);
            if (fabricante.matches("[A-Z0-9]{4}")) {
                return List.of(fabricante + snComun.substring(8), snComun);
            }
        }
        return List.of(snComun);
    }

    /** Copia por OLT (get_all_onus_details?olt_id=…), 10 minutos. */
    private final Map<String, List<OnuCandidate>> porOlt = new java.util.HashMap<>();
    private final Map<String, Instant> porOltLeidaEn = new java.util.HashMap<>();

    private List<OnuCandidate> deLaOlt(String oltCodigo) {
        Instant ahora = reloj.instant();
        String clave = oltCodigo.toUpperCase(Locale.ROOT);
        // Si el inventario completo está fresco, sale de ahí sin gastar otra llamada.
        if (Duration.between(inventarioLeidoEn, ahora).compareTo(VIGENCIA_INVENTARIO) <= 0) {
            return inventario.stream().filter(o -> oltCodigo.equalsIgnoreCase(o.position().oltCodigo())).toList();
        }
        Instant leida = porOltLeidaEn.get(clave);
        if (leida != null && Duration.between(leida, ahora).compareTo(VIGENCIA_INVENTARIO) <= 0) {
            return porOlt.get(clave);
        }
        if (!hayCupo(ahora)) {
            if (porOlt.containsKey(clave)) {
                return porOlt.get(clave);
            }
            if (!inventario.isEmpty()) {
                return inventario.stream().filter(o -> oltCodigo.equalsIgnoreCase(o.position().oltCodigo())).toList();
            }
            throw new com.guajiranet.migradoronu.integraciones.http.ErrorDeIntegracion(SmartOltClient.SISTEMA,
                    "Todavía no hay copia del inventario de SmartOLT y se llegó al máximo de descargas de esta hora (SmartOLT permite 15 y bloquea al pasarse). La copia se renueva sola cada 15 minutos: intenta en unos minutos.", null);
        }
        descargas.addLast(ahora);
        List<OnuCandidate> onus = convertir(cliente.get().onus(Map.of("olt_id", oltIdDe(oltCodigo))));
        porOlt.put(clave, onus);
        porOltLeidaEn.put(clave, ahora);
        return onus;
    }

    /**
     * Descarga el inventario completo para la copia (la precarga, en su propio hilo). La descarga va fuera del candado:
     * puede tardar y las pantallas siguen leyendo la copia anterior mientras tanto. false si no quedaba cupo.
     */
    public boolean refrescarInventario() {
        synchronized (this) {
            if (!hayCupo(reloj.instant())) {
                return false;
            }
            descargas.addLast(reloj.instant());
        }
        List<JsonNode> registros = cliente.get().onus(Map.of());
        synchronized (this) {
            inventario = convertir(registros);
            inventarioLeidoEn = reloj.instant();
            // Las copias por OLT quedan viejas al lado de esta: se sirve todo de la nueva.
            porOlt.clear();
            porOltLeidaEn.clear();
            return true;
        }
    }

    /** ¿Queda cupo de get_all_onus_details en la última hora? */
    private boolean hayCupo(Instant ahora) {
        while (!descargas.isEmpty() && Duration.between(descargas.peekFirst(), ahora).compareTo(Duration.ofHours(1)) > 0) {
            descargas.pollFirst();
        }
        return descargas.size() < MAXIMO_INVENTARIOS_POR_HORA;
    }

    private List<OnuCandidate> inventarioCompleto() {
        Instant ahora = reloj.instant();
        if (Duration.between(inventarioLeidoEn, ahora).compareTo(VIGENCIA_INVENTARIO) <= 0) {
            return inventario;
        }
        if (!hayCupo(ahora)) {
            if (!inventario.isEmpty()) {
                return inventario;
            }
            throw new com.guajiranet.migradoronu.integraciones.http.ErrorDeIntegracion(SmartOltClient.SISTEMA,
                    "Todavía no hay copia del inventario de SmartOLT y se llegó al máximo de descargas de esta hora (SmartOLT permite 15 y bloquea al pasarse). La copia se renueva sola cada 15 minutos: intenta en unos minutos.", null);
        }
        descargas.addLast(ahora);
        inventario = convertir(cliente.get().onus(Map.of()));
        inventarioLeidoEn = ahora;
        return inventario;
    }

    /** Las ONUs (SN en formato común) que en la última descarga venían sin External ID en SmartOLT. */
    private final java.util.Set<String> sinExternalId = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @Override
    public java.util.Set<String> sinExternalId() {
        return java.util.Set.copyOf(sinExternalId);
    }

    private List<OnuCandidate> convertir(List<JsonNode> registros) {
        for (JsonNode r : registros) {
            String sn = texto(r, "sn");
            if (sn == null) {
                continue;
            }
            String comun = FormatoSn.normalizar(sn).orElse(sn.toUpperCase(Locale.ROOT));
            String id = texto(r, "unique_external_id");
            if (id == null || id.isBlank()) {
                sinExternalId.add(comun);
            } else {
                sinExternalId.remove(comun);
            }
        }
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
        return Optional.of(new OnuCandidate(sn, com.guajiranet.migradoronu.conciliacion.dominio.MarcaDeOnu.de(sn, modelo), modelo,
                new OnuPosition(olt, "0/" + board, port, onu), enLinea, ip, perfil, gestion(r)));
    }

    /** VLAN de gestión, VLAN de los service-ports y TR-069 tal como los muestra SmartOLT. */
    static com.guajiranet.migradoronu.conciliacion.dominio.GestionOnu gestion(JsonNode r) {
        List<Integer> vlans = new java.util.ArrayList<>();
        JsonNode puertos = r.get("service_ports");
        if (puertos != null && puertos.isArray()) {
            puertos.forEach(p -> Optional.ofNullable(entero(p, "vlan")).ifPresent(vlans::add));
        }
        String tr069 = texto(r, "tr069");
        return new com.guajiranet.migradoronu.conciliacion.dominio.GestionOnu(texto(r, "mgmt_ip_mode"), entero(r, "mgmt_ip_vlan"),
                vlans, tr069 != null && tr069.equalsIgnoreCase("Enabled"));
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
