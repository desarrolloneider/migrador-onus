package com.guajiranet.migradoronu.conciliacion.aplicacion;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

import com.guajiranet.migradoronu.conciliacion.dominio.FormatoSn;
import com.guajiranet.migradoronu.conciliacion.dominio.OnuCandidate;
import com.guajiranet.migradoronu.conciliacion.puertos.salida.OltProfilesQueryPort;
import com.guajiranet.migradoronu.conciliacion.puertos.salida.OnuInventoryPort;

/**
 * Las ONUs de una OLT con su estado (de SmartOLT) y su perfil TR-069 (de la propia OLT), para escoger qué migrar.
 * Solo lectura en los dos sistemas.
 */
public class ConsultarOnusDeOlt {

    /** @param perfil el nombre del perfil en la OLT; null si no tiene o no se pudo leer */
    public record OnuDeOlt(String sn, String modelo, String tarjeta, int pon, int onuId, boolean enLinea, String ip, String perfil) {
    }

    /**
     * @param perfilesLeidos false si la OLT no tiene acceso por consola o no se pudo leer: los perfiles salen vacíos
     * @param porPerfil      cuántas ONUs hay por perfil ("Sin perfil" para las que no tienen)
     */
    public record OnusDeOlt(String olt, boolean perfilesLeidos, List<OnuDeOlt> onus, Map<String, Long> porPerfil) {
    }

    public static final String SIN_PERFIL = "Sin perfil";

    private final OnuInventoryPort smartOlt;
    private final OltProfilesQueryPort perfilesOlt;

    public ConsultarOnusDeOlt(OnuInventoryPort smartOlt, OltProfilesQueryPort perfilesOlt) {
        this.smartOlt = Objects.requireNonNull(smartOlt, "smartOlt");
        this.perfilesOlt = Objects.requireNonNull(perfilesOlt, "perfilesOlt");
    }

    public OnusDeOlt consultar(String oltCodigo) {
        List<OnuCandidate> candidatas = smartOlt.listOnus(null, oltCodigo, null, null);
        Optional<Map<String, String>> perfiles = perfilesOlt.perfiles(oltCodigo);
        List<OnuDeOlt> onus = candidatas.stream()
                .map(o -> new OnuDeOlt(FormatoSn.normalizar(o.rawSn()).orElse(o.rawSn()), o.modelo(), o.position().tarjeta(),
                        o.position().pon(), o.position().onuId(), o.enLinea(), o.ip(),
                        perfiles.map(m -> m.get(o.position().tarjeta() + "/" + o.position().pon() + ":" + o.position().onuId())).orElse(null)))
                .sorted(Comparator.comparing(OnuDeOlt::tarjeta).thenComparingInt(OnuDeOlt::pon).thenComparingInt(OnuDeOlt::onuId))
                .toList();
        Map<String, Long> porPerfil = new TreeMap<>();
        onus.forEach(o -> porPerfil.merge(o.perfil() == null ? SIN_PERFIL : o.perfil(), 1L, Long::sum));
        return new OnusDeOlt(oltCodigo, perfiles.isPresent(), onus, new LinkedHashMap<>(porPerfil));
    }
}
