package com.guajiranet.migradoronu.ensamblaje.conciliacion;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.guajiranet.migradoronu.conciliacion.dominio.FormatoSn;
import com.guajiranet.migradoronu.conciliacion.dominio.OnuCandidate;
import com.guajiranet.migradoronu.conciliacion.puertos.salida.OnuInventoryPort;
import com.guajiranet.migradoronu.ensamblaje.consola.GenieAcsPorApi;

/**
 * Solo administradores: las ONUs que tienen la VLAN 46 activa en SmartOLT, con la IP de gestión con que se reportaron a
 * GenieACS, marcando las IPs repetidas en la misma OLT (el DHCP de la VLAN 46 reparte la misma IP a varias). Desde ahí se
 * arma un lote "Quitar VLAN 46" para devolverlas. Sale de la copia del inventario de SmartOLT: no gasta descargas.
 */
@RestController
@RequestMapping("/api/v1/vlan-gestion")
public class VlanGestionController {

    private static final int VLAN = 46;

    public record OnuConVlanDto(String sn, String olt, String tarjeta, int pon, int onuId, boolean enLinea, String modelo, String ipGestion,
                                int repetidaEn, Instant ultimoReporteAcs) {
    }

    private final OnuInventoryPort smartOlt;
    private final GenieAcsPorApi genie;

    public VlanGestionController(OnuInventoryPort smartOlt, GenieAcsPorApi genie) {
        this.smartOlt = smartOlt;
        this.genie = genie;
    }

    @GetMapping
    public List<OnuConVlanDto> conVlanActiva(@RequestParam(required = false) String olt) {
        List<OnuCandidate> conVlan = smartOlt.listOnus(null, olt == null || olt.isBlank() ? null : olt, null, null).stream()
                .filter(o -> o.gestion() != null && o.gestion().gestionActiva() && o.gestion().vlanGestion() != null
                        && o.gestion().vlanGestion() == VLAN)
                .toList();
        Function<OnuCandidate, String> sn = o -> FormatoSn.normalizar(o.rawSn()).orElse(o.rawSn().toUpperCase(Locale.ROOT));
        Map<String, GenieAcsPorApi.IpDeGestion> ips;
        try {
            ips = genie.ipsDeGestion(conVlan.stream().map(sn).toList());
        } catch (RuntimeException e) {
            ips = Map.of();
        }
        Map<String, GenieAcsPorApi.IpDeGestion> porSn = ips;
        // Cuántas ONUs de la misma OLT tienen la misma IP de gestión.
        Map<String, Long> repetidas = conVlan.stream()
                .map(o -> {
                    var g = porSn.get(sn.apply(o));
                    return g == null || g.ip() == null ? null : o.position().oltCodigo() + "|" + g.ip();
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        return conVlan.stream().map(o -> {
            var g = porSn.get(sn.apply(o));
            String ip = g == null ? null : g.ip();
            int veces = ip == null ? 0 : repetidas.getOrDefault(o.position().oltCodigo() + "|" + ip, 1L).intValue();
            return new OnuConVlanDto(sn.apply(o), o.position().oltCodigo(), o.position().tarjeta(), o.position().pon(), o.position().onuId(),
                    o.enLinea(), o.modelo(), ip, veces, g == null ? null : g.ultimoReporte());
        }).sorted(Comparator.comparing(OnuConVlanDto::olt).thenComparing(OnuConVlanDto::repetidaEn, Comparator.reverseOrder())
                .thenComparing(d -> d.ipGestion() == null ? "" : d.ipGestion())).toList();
    }
}
