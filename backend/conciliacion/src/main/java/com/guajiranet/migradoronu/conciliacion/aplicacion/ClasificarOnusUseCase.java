package com.guajiranet.migradoronu.conciliacion.aplicacion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.guajiranet.migradoronu.conciliacion.dominio.Classification;
import com.guajiranet.migradoronu.conciliacion.dominio.DuplicateRecord;
import com.guajiranet.migradoronu.conciliacion.dominio.ExclusionReason;
import com.guajiranet.migradoronu.conciliacion.dominio.FormatoSn;
import com.guajiranet.migradoronu.conciliacion.dominio.OnuCandidate;
import com.guajiranet.migradoronu.conciliacion.dominio.OnuPosition;
import com.guajiranet.migradoronu.conciliacion.dominio.Subscriber;
import com.guajiranet.migradoronu.conciliacion.dominio.UnreviewedFailure;
import com.guajiranet.migradoronu.conciliacion.puertos.entrada.ClassifyOnusPort;
import com.guajiranet.migradoronu.conciliacion.puertos.salida.OltProfilesQueryPort;
import com.guajiranet.migradoronu.conciliacion.puertos.salida.OltSettingsQueryPort;
import com.guajiranet.migradoronu.conciliacion.puertos.salida.OnuInventoryPort;
import com.guajiranet.migradoronu.conciliacion.puertos.salida.OnuParticipationQueryPort;
import com.guajiranet.migradoronu.conciliacion.puertos.salida.SubscriberDirectoryPort;

/**
 * El recorrido K1 a K11 (spec 8.2). Lee SmartOLT y Wispro una sola vez por vista previa y cruza por IP en memoria
 * (RN-01). Ante cualquier duda la ONU queda fuera con su motivo: nunca se escoge por ella (RN-13, RN-35).
 */
public class ClasificarOnusUseCase implements ClassifyOnusPort {

    private final OnuInventoryPort smartOlt;
    private final SubscriberDirectoryPort wispro;
    private final OltSettingsQueryPort olts;
    private final OnuParticipationQueryPort participacion;
    private final Supplier<String> perfilDestino;
    private final Set<String> marcasConProcedimiento;
    private final boolean exigirContrato;
    private final OltProfilesQueryPort perfilesOlt;
    private final Supplier<String> perfilOrigen;

    public ClasificarOnusUseCase(OnuInventoryPort smartOlt, SubscriberDirectoryPort wispro, OltSettingsQueryPort olts,
                                 OnuParticipationQueryPort participacion, Supplier<String> perfilDestino,
                                 Set<String> marcasConProcedimiento) {
        this(smartOlt, wispro, olts, participacion, perfilDestino, marcasConProcedimiento, null, () -> null);
    }

    /**
     * @param perfilesOlt  el perfil de cada ONU según la OLT; SmartOLT no lo trae
     * @param perfilOrigen el único perfil que se cambia (GUAJIRANET); las demás quedan fuera con EXC_OTRO_PERFIL
     */
    public ClasificarOnusUseCase(OnuInventoryPort smartOlt, SubscriberDirectoryPort wispro, OltSettingsQueryPort olts,
                                 OnuParticipationQueryPort participacion, Supplier<String> perfilDestino,
                                 Set<String> marcasConProcedimiento, OltProfilesQueryPort perfilesOlt, Supplier<String> perfilOrigen) {
        this(smartOlt, wispro, olts, participacion, perfilDestino, marcasConProcedimiento, perfilesOlt, perfilOrigen, true);
    }

    /** @param exigirContrato si es false, las ONUs en línea sin IP o sin un único contrato en Wispro también entran */
    public ClasificarOnusUseCase(OnuInventoryPort smartOlt, SubscriberDirectoryPort wispro, OltSettingsQueryPort olts,
                                 OnuParticipationQueryPort participacion, Supplier<String> perfilDestino,
                                 Set<String> marcasConProcedimiento, OltProfilesQueryPort perfilesOlt, Supplier<String> perfilOrigen,
                                 boolean exigirContrato) {
        this.exigirContrato = exigirContrato;
        // Null: no se consulta la OLT y el perfil es el que traiga SmartOLT.
        this.perfilesOlt = perfilesOlt;
        this.perfilOrigen = Objects.requireNonNull(perfilOrigen, "perfilOrigen");
        this.smartOlt = Objects.requireNonNull(smartOlt, "smartOlt");
        this.wispro = Objects.requireNonNull(wispro, "wispro");
        this.olts = Objects.requireNonNull(olts, "olts");
        this.participacion = Objects.requireNonNull(participacion, "participacion");
        this.perfilDestino = Objects.requireNonNull(perfilDestino, "perfilDestino");
        this.marcasConProcedimiento = marcasConProcedimiento.stream().map(m -> m.toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
    }

    @Override
    public List<Classification> classify(ClassifyOnusCommand command) {
        List<OnuCandidate> onus = smartOlt.listOnus(command.marca(), command.oltCodigo(), command.tarjeta(), command.pon());
        if (command.sns() != null) {
            // Solo las escogidas en la pantalla, comparadas en el formato común del SN.
            Set<String> escogidas = command.sns().stream().map(sn -> FormatoSn.normalizar(sn).orElse(sn.strip().toUpperCase(Locale.ROOT)))
                    .collect(Collectors.toSet());
            onus = onus.stream().filter(o -> escogidas.contains(FormatoSn.normalizar(o.rawSn()).orElse(o.rawSn().toUpperCase(Locale.ROOT))))
                    .toList();
        }
        origenes.set(command.perfilesOrigen());
        try {
            return clasificar(onus, porIp(wispro.listAll()));
        } finally {
            origenes.remove();
        }
    }

    /** Los perfiles de origen de la clasificación en curso; null: el configurado. */
    private final ThreadLocal<List<String>> origenes = new ThreadLocal<>();

    /** ¿Se cambia una ONU con este perfil (null = sin perfil)? */
    private boolean esOrigen(String perfil) {
        List<String> escogidos = origenes.get();
        if (escogidos == null) {
            // Por defecto se cambian las del perfil origen (GUAJIRANET) y también las que no tienen perfil.
            String origen = perfilOrigen.get();
            return origen == null || perfil == null || origen.equalsIgnoreCase(perfil);
        }
        return perfil == null ? escogidos.contains(ClassifyOnusCommand.SIN_PERFIL)
                : escogidos.stream().anyMatch(p -> p.equalsIgnoreCase(perfil));
    }

    private String descripcionOrigen() {
        List<String> escogidos = origenes.get();
        if (escogidos == null) {
            return perfilOrigen.get() + " o sin perfil";
        }
        return escogidos.stream().map(p -> p.equals(ClassifyOnusCommand.SIN_PERFIL) ? "sin perfil" : p).collect(Collectors.joining(" o "));
    }

    @Override
    public Optional<Classification> classifySingle(String sn) {
        String buscado = FormatoSn.normalizar(sn).orElse(sn.strip().toUpperCase(Locale.ROOT));
        List<OnuCandidate> registros = smartOlt.findBySn(sn);
        if (registros.isEmpty()) {
            return Optional.empty();
        }
        // En la prueba individual el SN se escribe a mano: se acepta el perfil origen y también una ONU sin perfil.
        String origen = perfilOrigen.get();
        origenes.set(origen == null ? null : List.of(origen, ClassifyOnusCommand.SIN_PERFIL));
        try {
            return clasificarUnaPorSn(registros, buscado, sn);
        } finally {
            origenes.remove();
        }
    }

    private Optional<Classification> clasificarUnaPorSn(List<OnuCandidate> registros, String buscado, String sn) {
        return clasificar(registros, porIp(wispro.listAll())).stream()
                .filter(c -> c.sn().equals(buscado) || c.onu().rawSn().equalsIgnoreCase(sn.strip()))
                .findFirst();
    }

    private List<Classification> clasificar(List<OnuCandidate> onus, Map<String, List<Subscriber>> contratosPorIp) {
        perfilesLeidos.clear();
        Map<String, List<OnuCandidate>> porSn = new LinkedHashMap<>();
        List<Classification> resultado = new ArrayList<>();
        for (OnuCandidate onu : onus) {
            Optional<String> sn = FormatoSn.normalizar(onu.rawSn());
            if (sn.isEmpty()) {
                // K1
                resultado.add(Classification.excluded(onu.rawSn(), ExclusionReason.EXC_SN_FORMATO,
                        "ONU %s no entra al lote: su SN en SmartOLT tiene un formato que no se pudo llevar al formato común (%s)."
                                .formatted(onu.rawSn(), onu.rawSn()), onu, null, List.of()));
            } else {
                porSn.computeIfAbsent(sn.get(), k -> new ArrayList<>()).add(onu);
            }
        }
        porSn.forEach((sn, registros) -> resultado.add(clasificarUna(sn, registros, contratosPorIp)));
        return resultado;
    }

    private Classification clasificarUna(String sn, List<OnuCandidate> registros, Map<String, List<Subscriber>> contratosPorIp) {
        // K2 (RN-35): con varios registros se usa el único en línea.
        OnuCandidate onu = registros.getFirst();
        List<DuplicateRecord> duplicados = List.of();
        if (registros.size() > 1) {
            List<OnuCandidate> enLinea = registros.stream().filter(OnuCandidate::enLinea).toList();
            List<DuplicateRecord> todos = registros.stream().map(r -> new DuplicateRecord(r.position(), r.enLinea())).toList();
            if (enLinea.size() != 1) {
                return Classification.excluded(sn, ExclusionReason.EXC_SN_DUPLICADO_SIN_VIGENTE,
                        "ONU %s no entra al lote: SmartOLT la muestra %d veces (%s) y %s en línea. No se escoge cuál es la buena (RN-35)."
                                .formatted(sn, registros.size(), lista(registros), enLinea.isEmpty() ? "ninguna está" : "más de una está"),
                        onu, null, todos);
            }
            onu = enLinea.getFirst();
            OnuCandidate vigente = onu;
            duplicados = registros.stream().filter(r -> r != vigente).map(r -> new DuplicateRecord(r.position(), r.enLinea())).toList();
        }
        OnuPosition pos = onu.position();
        // K3
        // "*": cualquier marca de ONU. El comando lo ejecuta la OLT, así que lo que manda es que la OLT tenga consola.
        if (!marcasConProcedimiento.contains("*")
                && (onu.marca() == null || !marcasConProcedimiento.contains(onu.marca().toUpperCase(Locale.ROOT)))) {
            return Classification.excluded(sn, ExclusionReason.EXC_MARCA_SIN_PROCEDIMIENTO,
                    "ONU %s es %s; esa marca todavía no tiene procedimiento.".formatted(sn, onu.marca() == null ? "de marca desconocida" : onu.marca()),
                    onu, null, duplicados);
        }
        // El perfil real lo dice la OLT. Si la OLT tiene consola y no se pudo leer, no se adivina.
        boolean conConsola = perfilesOlt != null && olts.consoleAccessConfigured(pos.oltCodigo());
        Optional<Map<String, String>> perfilesDeLaOlt = conConsola ? perfilesDe(pos.oltCodigo()) : Optional.empty();
        if (conConsola && perfilesDeLaOlt.isEmpty()) {
            return Classification.excluded(sn, ExclusionReason.EXC_PERFIL_NO_LEIDO,
                    "ONU %s no entra al lote: no se pudo leer en %s qué perfil TR-069 tiene.".formatted(sn, pos.oltCodigo()), onu, null, duplicados);
        }
        if (perfilesDeLaOlt.isPresent()) {
            String perfilEnOlt = perfilesDeLaOlt.get().get(pos.tarjeta() + "/" + pos.pon() + ":" + pos.onuId());
            onu = new OnuCandidate(onu.rawSn(), onu.marca(), onu.modelo(), pos, onu.enLinea(), onu.ip(), perfilEnOlt);
        }
        List<Subscriber> contratos = onu.ip() == null ? List.of() : contratosPorIp.getOrDefault(onu.ip(), List.of());
        Subscriber contrato = contratos.size() == 1 ? contratos.getFirst() : null;
        // Las que ya tienen el perfil nuevo no son exclusiones (spec 8.2).
        if (onu.perfilTr069() != null && onu.perfilTr069().equalsIgnoreCase(perfilDestino.get())) {
            return Classification.alreadyHasProfile(sn, onu, contrato, duplicados);
        }
        if (perfilesDeLaOlt.isPresent() && !esOrigen(onu.perfilTr069())) {
            return Classification.excluded(sn, ExclusionReason.EXC_OTRO_PERFIL,
                    "ONU %s tiene %s en %s; en este lote solo se cambian las que tienen %s.".formatted(sn,
                            onu.perfilTr069() == null ? "ningún perfil TR-069" : "el perfil " + onu.perfilTr069(), pos.oltCodigo(),
                            descripcionOrigen()),
                    onu, contrato, duplicados);
        }
        // K4
        if (!onu.enLinea()) {
            return Classification.excluded(sn, ExclusionReason.EXC_FUERA_DE_LINEA,
                    "ONU %s está fuera de línea en SmartOLT.".formatted(sn), onu, null, duplicados);
        }
        // Sin exigir contrato: el cambio lo hace la OLT por posición y SN, así que una ONU en línea sin IP o sin contrato
        // único en Wispro también entra. El contrato solo se muestra cuando hay exactamente uno.
        if (!exigirContrato) {
            return validarOltYLotes(sn, onu, pos, contrato, duplicados);
        }
        // K5
        if (onu.ip() == null || onu.ip().isBlank()) {
            return Classification.excluded(sn, ExclusionReason.EXC_SIN_IP_SMARTOLT,
                    "ONU %s no entra al lote: SmartOLT no muestra su IP, y sin IP no se puede saber de qué contrato es.".formatted(sn),
                    onu, null, duplicados);
        }
        // K6 y K7
        if (contratos.isEmpty()) {
            return Classification.excluded(sn, ExclusionReason.EXC_IP_NO_EN_WISPRO,
                    "ONU %s no entra al lote: su IP %s (según SmartOLT) no está en ningún contrato de Wispro.".formatted(sn, onu.ip()),
                    onu, null, duplicados);
        }
        if (contratos.size() > 1) {
            return Classification.excluded(sn, ExclusionReason.EXC_IP_EN_VARIOS_CONTRATOS,
                    "ONU %s no entra al lote: su IP %s aparece en %d contratos de Wispro (%s)."
                            .formatted(sn, onu.ip(), contratos.size(), contratos.stream().map(Subscriber::contrato).collect(Collectors.joining(", "))),
                    onu, null, duplicados);
        }
        return validarOltYLotes(sn, onu, pos, contrato, duplicados);
    }

    private Classification validarOltYLotes(String sn, OnuCandidate onu, OnuPosition pos, Subscriber contrato,
                                            List<DuplicateRecord> duplicados) {
        // K8
        if (!olts.consoleAccessConfigured(pos.oltCodigo())) {
            return Classification.excluded(sn, ExclusionReason.EXC_OLT_SIN_ACCESO_CONFIGURADO,
                    "ONU %s está en %s, que no tiene configurado el acceso por consola.".formatted(sn, pos.oltCodigo()),
                    onu, contrato, duplicados);
        }
        if (!olts.allowedBoards(pos.oltCodigo()).contains(pos.tarjeta())) {
            return Classification.excluded(sn, ExclusionReason.EXC_TARJETA_NO_PERMITIDA,
                    "ONU %s está en la tarjeta %s de %s, que no está en la lista de tarjetas permitidas de esa OLT."
                            .formatted(sn, pos.tarjeta(), pos.oltCodigo()), onu, contrato, duplicados);
        }
        // K10
        Optional<String> otroLote = participacion.activeBatchNumberOf(sn);
        if (otroLote.isPresent()) {
            return Classification.excluded(sn, ExclusionReason.EXC_EN_OTRO_LOTE,
                    "ONU %s ya está en el lote %s, que sigue activo.".formatted(sn, otroLote.get()), onu, contrato, duplicados);
        }
        // K11
        Optional<UnreviewedFailure> fallida = participacion.unreviewedFailureOf(sn);
        if (fallida.isPresent()) {
            return Classification.excluded(sn, ExclusionReason.EXC_FALLIDA_SIN_REVISAR,
                    "ONU %s falló en el lote %s (paso %s) y nadie la ha revisado.".formatted(sn, fallida.get().loteNumero(), fallida.get().paso()),
                    onu, contrato, duplicados);
        }
        return Classification.included(sn, onu, contrato, duplicados);
    }

    /** Una lectura por OLT en cada clasificación. */
    private final Map<String, Optional<Map<String, String>>> perfilesLeidos = new java.util.concurrent.ConcurrentHashMap<>();

    private Optional<Map<String, String>> perfilesDe(String olt) {
        return perfilesLeidos.computeIfAbsent(olt, perfilesOlt::perfiles);
    }

    private static Map<String, List<Subscriber>> porIp(List<Subscriber> contratos) {
        return contratos.stream().filter(s -> s.ip() != null && !s.ip().isBlank())
                .collect(Collectors.groupingBy(s -> s.ip().strip(), LinkedHashMap::new, Collectors.toList()));
    }

    private static String lista(List<OnuCandidate> registros) {
        Function<OnuCandidate, String> texto = r -> "%s %s/%d ID %d%s".formatted(r.position().oltCodigo(), r.position().tarjeta(),
                r.position().pon(), r.position().onuId(), r.enLinea() ? " en línea" : " fuera de línea");
        return registros.stream().map(texto).collect(Collectors.joining("; "));
    }
}
