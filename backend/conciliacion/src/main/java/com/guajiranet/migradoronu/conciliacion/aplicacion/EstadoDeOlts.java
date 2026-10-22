package com.guajiranet.migradoronu.conciliacion.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.guajiranet.migradoronu.conciliacion.dominio.OnuCandidate;
import com.guajiranet.migradoronu.conciliacion.puertos.salida.OltProfilesQueryPort;
import com.guajiranet.migradoronu.conciliacion.puertos.salida.OltSettingsQueryPort;
import com.guajiranet.migradoronu.conciliacion.puertos.salida.OnuInventoryPort;
import com.guajiranet.migradoronu.conciliacion.puertos.salida.SubscriberDirectoryPort;

/**
 * El tablero del estado de cada OLT: cuántas ONUs tiene, cuántas en línea y fuera, cuántas sin contrato en Wispro y,
 * si la OLT tiene consola, cuántas por perfil TR-069 (leído en la OLT). Solo lectura: no cambia nada en ningún sistema.
 * SmartOLT, Wispro y los perfiles salen de sus cachés, así que refrescar seguido no sobrecarga nada.
 */
public class EstadoDeOlts {

    public record Tarjeta(String tarjeta, long total, long enLinea) {
    }

    public record Marca(String marca, long total, long enLinea) {
    }

    /**
     * @param perfiles   cuántas por perfil ("Sin perfil" para las que no tienen); null si no se pudo leer en la OLT
     * @param pendientes en línea y sin el perfil destino: las que entrarían a un lote; null si no se leyeron perfiles
     */
    public record Olt(String codigo, long total, long enLinea, long fueraDeLinea, long sinContrato, long sinContratoEnLinea,
                      boolean accesoConsola, Map<String, Long> perfiles, Long conPerfilDestino, Long pendientes,
                      List<Tarjeta> tarjetas, List<Marca> marcas, long conVlanGestion, long conVlanGestionEnLinea, Long migradas) {
    }

    public record Totales(long olts, long total, long enLinea, long fueraDeLinea, long sinContrato, long conPerfilDestino,
                          long pendientes, long conVlanGestion, Long migradas) {
    }

    public record Estado(Instant leidoEn, String perfilDestino, int vlanGestion, Totales totales, List<Marca> marcas, List<Olt> olts) {
    }

    private final OnuInventoryPort smartOlt;
    private final SubscriberDirectoryPort wispro;
    private final OltSettingsQueryPort olts;
    private final OltProfilesQueryPort perfilesOlt;
    private final Supplier<String> perfilDestino;
    private final Clock reloj;
    private final int vlanGestion;
    /** Las ONUs que GenieACS ya muestra con TR069_INTERNET (servicio WAN migrado), por SN en formato común. */
    private java.util.function.Supplier<java.util.Set<String>> migradas = () -> null;

    public EstadoDeOlts conMigradas(java.util.function.Supplier<java.util.Set<String>> migradas) {
        this.migradas = Objects.requireNonNull(migradas, "migradas");
        return this;
    }

    public EstadoDeOlts(OnuInventoryPort smartOlt, SubscriberDirectoryPort wispro, OltSettingsQueryPort olts,
                        OltProfilesQueryPort perfilesOlt, Supplier<String> perfilDestino, Clock reloj, int vlanGestion) {
        this.vlanGestion = vlanGestion;
        this.smartOlt = Objects.requireNonNull(smartOlt, "smartOlt");
        this.wispro = Objects.requireNonNull(wispro, "wispro");
        this.olts = Objects.requireNonNull(olts, "olts");
        this.perfilesOlt = Objects.requireNonNull(perfilesOlt, "perfilesOlt");
        this.perfilDestino = Objects.requireNonNull(perfilDestino, "perfilDestino");
        this.reloj = Objects.requireNonNull(reloj, "reloj");
    }

    public Estado consultar() {
        List<OnuCandidate> todas = smartOlt.listOnus(null, null, null, null);
        Map<String, Long> contratosPorIp = wispro.listAll().stream()
                .filter(s -> s.ip() != null && !s.ip().isBlank())
                .collect(Collectors.groupingBy(s -> s.ip().strip(), Collectors.counting()));
        Map<String, List<OnuCandidate>> porOlt = todas.stream()
                .collect(Collectors.groupingBy(o -> o.position().oltCodigo(), TreeMap::new, Collectors.toList()));
        String destino = perfilDestino.get();
        java.util.Set<String> listas;
        try {
            listas = migradas.get();
        } catch (RuntimeException e) {
            listas = null;
        }
        java.util.Set<String> yaMigradas = listas;

        // Los perfiles se leen en las OLT con consola, todas a la vez (cada lectura tiene su caché de 2 minutos).
        Map<String, CompletableFuture<Optional<Map<String, String>>>> lecturas = new LinkedHashMap<>();
        porOlt.keySet().forEach(codigo -> lecturas.put(codigo, olts.consoleAccessConfigured(codigo)
                ? CompletableFuture.supplyAsync(() -> leerPerfiles(codigo))
                : CompletableFuture.completedFuture(Optional.empty())));

        List<Olt> resultado = new ArrayList<>();
        porOlt.forEach((codigo, onus) -> {
            Optional<Map<String, String>> perfiles = lecturas.get(codigo).join();
            long enLinea = onus.stream().filter(OnuCandidate::enLinea).count();
            long sinContrato = onus.stream().filter(o -> !conContrato(o, contratosPorIp)).count();
            long sinContratoEnLinea = onus.stream().filter(o -> o.enLinea() && !conContrato(o, contratosPorIp)).count();
            Map<String, Long> porPerfil = null;
            Long conDestino = null;
            Long pendientes = null;
            if (perfiles.isPresent()) {
                Map<String, String> mapa = perfiles.get();
                porPerfil = new TreeMap<>();
                long destinoN = 0;
                long pend = 0;
                for (OnuCandidate o : onus) {
                    String perfil = mapa.get(o.position().tarjeta() + "/" + o.position().pon() + ":" + o.position().onuId());
                    porPerfil.merge(perfil == null ? ConsultarOnusDeOlt.SIN_PERFIL : perfil, 1L, Long::sum);
                    boolean tieneDestino = perfil != null && perfil.equalsIgnoreCase(destino);
                    if (tieneDestino) {
                        destinoN++;
                    } else if (o.enLinea()) {
                        pend++;
                    }
                }
                conDestino = destinoN;
                pendientes = pend;
            }
            List<Tarjeta> tarjetas = onus.stream()
                    .collect(Collectors.groupingBy(o -> o.position().tarjeta(), TreeMap::new, Collectors.toList()))
                    .entrySet().stream()
                    .map(e -> new Tarjeta(e.getKey(), e.getValue().size(), e.getValue().stream().filter(OnuCandidate::enLinea).count()))
                    .toList();
            List<Marca> marcas = porMarca(onus);
            long conVlan = onus.stream().filter(this::tieneGestion).count();
            long conVlanEnLinea = onus.stream().filter(o -> o.enLinea() && tieneGestion(o)).count();
            Long migradasOlt = yaMigradas == null ? null : onus.stream().filter(o -> yaMigradas.contains(
                    com.guajiranet.migradoronu.conciliacion.dominio.FormatoSn.normalizar(o.rawSn()).orElse(o.rawSn().toUpperCase(java.util.Locale.ROOT))))
                    .count();
            resultado.add(new Olt(codigo, onus.size(), enLinea, onus.size() - enLinea, sinContrato, sinContratoEnLinea,
                    olts.consoleAccessConfigured(codigo), porPerfil, conDestino, pendientes, tarjetas, marcas, conVlan, conVlanEnLinea,
                    migradasOlt));
        });
        resultado.sort(Comparator.comparingLong(Olt::total).reversed());

        Totales totales = new Totales(resultado.size(),
                resultado.stream().mapToLong(Olt::total).sum(),
                resultado.stream().mapToLong(Olt::enLinea).sum(),
                resultado.stream().mapToLong(Olt::fueraDeLinea).sum(),
                resultado.stream().mapToLong(Olt::sinContrato).sum(),
                resultado.stream().map(Olt::conPerfilDestino).filter(Objects::nonNull).mapToLong(Long::longValue).sum(),
                resultado.stream().map(Olt::pendientes).filter(Objects::nonNull).mapToLong(Long::longValue).sum(),
                resultado.stream().mapToLong(Olt::conVlanGestion).sum(),
                yaMigradas == null ? null : resultado.stream().map(Olt::migradas).filter(Objects::nonNull).mapToLong(Long::longValue).sum());
        return new Estado(reloj.instant(), destino, vlanGestion, totales, porMarca(todas), resultado);
    }

    /** La gestión del procedimiento del NOC: IP de gestión por DHCP en la VLAN de gestión (46). */
    private boolean tieneGestion(OnuCandidate o) {
        return o.gestion() != null && o.gestion().gestionActiva() && o.gestion().vlanGestion() != null
                && o.gestion().vlanGestion() == vlanGestion;
    }

    /** Total y en línea por marca, de la que más tiene a la que menos. */
    private static List<Marca> porMarca(List<OnuCandidate> onus) {
        return onus.stream()
                .collect(Collectors.groupingBy(o -> o.marca() == null ? "Desconocida" : o.marca(), Collectors.toList()))
                .entrySet().stream()
                .map(e -> new Marca(e.getKey(), e.getValue().size(), e.getValue().stream().filter(OnuCandidate::enLinea).count()))
                .sorted(Comparator.comparingLong(Marca::total).reversed())
                .toList();
    }

    private Optional<Map<String, String>> leerPerfiles(String codigo) {
        try {
            return perfilesOlt.perfiles(codigo);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** Tiene contrato si su IP está en exactamente un contrato de Wispro. */
    private static boolean conContrato(OnuCandidate o, Map<String, Long> contratosPorIp) {
        return o.ip() != null && contratosPorIp.getOrDefault(o.ip().strip(), 0L) == 1L;
    }
}
