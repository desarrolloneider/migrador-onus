package com.guajiranet.migradoronu.lotes.aplicacion;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import com.guajiranet.migradoronu.comun.errores.ErrorDeNegocio;
import com.guajiranet.migradoronu.comun.puertos.ActorType;
import com.guajiranet.migradoronu.comun.puertos.AuditAction;
import com.guajiranet.migradoronu.comun.puertos.AuditEntry;
import com.guajiranet.migradoronu.comun.puertos.AuditTrailPort;
import com.guajiranet.migradoronu.lotes.dominio.BatchFilters;
import com.guajiranet.migradoronu.lotes.dominio.BatchMode;
import com.guajiranet.migradoronu.lotes.dominio.BatchNumber;
import com.guajiranet.migradoronu.lotes.dominio.BatchStatus;
import com.guajiranet.migradoronu.lotes.dominio.ClassifiedOnu;
import com.guajiranet.migradoronu.lotes.dominio.ItemGroup;
import com.guajiranet.migradoronu.lotes.dominio.ParametersSnapshot;
import com.guajiranet.migradoronu.lotes.dominio.ScheduledOnu;
import com.guajiranet.migradoronu.lotes.puertos.entrada.BuildBatchPort;
import com.guajiranet.migradoronu.lotes.puertos.entrada.ControlBatchPort;
import com.guajiranet.migradoronu.lotes.puertos.entrada.QueryBatchDetailsPort;
import com.guajiranet.migradoronu.lotes.puertos.salida.BatchRepositoryPort;
import com.guajiranet.migradoronu.lotes.puertos.salida.OnuClassifierPort;
import com.guajiranet.migradoronu.lotes.puertos.salida.ParametersSnapshotPort;
import com.guajiranet.migradoronu.lotes.puertos.salida.RunSchedulerPort;
import com.guajiranet.migradoronu.lotes.puertos.salida.TransactionPort;

/**
 * Armar, confirmar, descartar y detener lotes (ESP-03 a ESP-05, ESP-10 a ESP-12). Solo las transiciones de la
 * spec 7.1. Una acción repetida sobre un lote que ya pasó a ese estado devuelve el lote tal como está (RN-24).
 */
public class LotesUseCase implements BuildBatchPort, ControlBatchPort, QueryBatchDetailsPort {

    static final String HUELLA = "_huella";
    /** Perfiles de origen escogidos al armar ("SIN_PERFIL" para las sin perfil); los lee el ejecutor. */
    static final String PERFILES_ORIGEN = "_perfiles_origen";
    /** SN escogidos uno por uno al armar, para repetir la simulación de verdad con las mismas ONUs. */
    static final String SNS = "_sns";

    private final OnuClassifierPort clasificador;
    private final ParametersSnapshotPort parametros;
    private final RunSchedulerPort ejecuciones;
    private final BatchRepositoryPort lotes;
    private final TransactionPort transacciones;
    private final AuditTrailPort auditoria;
    private final Supplier<String> procedimientoVersion;
    private final Clock reloj;

    public LotesUseCase(OnuClassifierPort clasificador, ParametersSnapshotPort parametros, RunSchedulerPort ejecuciones,
                        BatchRepositoryPort lotes, TransactionPort transacciones, AuditTrailPort auditoria,
                        Supplier<String> procedimientoVersion, Clock reloj) {
        this.clasificador = Objects.requireNonNull(clasificador, "clasificador");
        this.parametros = Objects.requireNonNull(parametros, "parametros");
        this.ejecuciones = Objects.requireNonNull(ejecuciones, "ejecuciones");
        this.lotes = Objects.requireNonNull(lotes, "lotes");
        this.transacciones = Objects.requireNonNull(transacciones, "transacciones");
        this.auditoria = Objects.requireNonNull(auditoria, "auditoria");
        this.procedimientoVersion = Objects.requireNonNull(procedimientoVersion, "procedimientoVersion");
        this.reloj = Objects.requireNonNull(reloj, "reloj");
    }

    // ---- Armar

    @Override
    public BatchView buildPreview(BuildBatchCommand command) {
        List<ClassifiedOnu> clasificadas = clasificador.classify(command.filtros());
        ParametersSnapshot snapshot = parametros.current();
        long entran = clasificadas.stream().filter(c -> c.grupo() == ItemGroup.ENTRA).count();
        int maximo = entero(snapshot, "tamano_maximo_lote", 50);
        if (entran > maximo) {
            throw ErrorDeNegocio.datosInvalidos("La vista previa tiene " + entran + " ONUs que entran y el máximo por lote es "
                    + maximo + ". Escoge una OLT, una tarjeta o un puerto para armar un lote más pequeño.");
        }
        return guardar(BatchMode.LOTE, command.filtros(), command.usuarioId(), clasificadas, snapshot);
    }

    @Override
    public BatchView buildSingleOnuPreview(BuildSingleOnuPreviewCommand command) {
        ClassifiedOnu onu = clasificador.classifySingle(command.sn())
                .orElseThrow(() -> ErrorDeNegocio.noEncontrado("La ONU " + command.sn() + " no está en SmartOLT."));
        // El ejecutor acepta como origen el perfil que la OLT mostró al armar (o "sin perfil"), y nada más.
        BatchFilters filtros = new BatchFilters(onu.marca() == null ? "Desconocida" : onu.marca(), null, null, null,
                List.of(onu.perfilActual() == null ? "SIN_PERFIL" : onu.perfilActual()), List.of(onu.sn()));
        return guardar(BatchMode.INDIVIDUAL, filtros, command.usuarioId(), List.of(onu), parametros.current());
    }

    @Override
    public BatchView rebuildFromSimulation(long loteId, long usuarioId) {
        LoteDetalle lote = lotes.detalle(loteId).orElseThrow(() -> ErrorDeNegocio.noEncontrado("No existe el lote " + loteId + "."));
        if (!lote.simulacion() || !lote.estado().isFinal() || lote.estado() == BatchStatus.DESCARTADO) {
            throw ErrorDeNegocio.conflicto("Solo se puede repetir de verdad una simulación que ya terminó.");
        }
        if (lote.modo() == BatchMode.INDIVIDUAL) {
            String sn = lotes.items(loteId).stream().findFirst().map(ClassifiedOnu::sn)
                    .orElseThrow(() -> ErrorDeNegocio.conflicto("La simulación no tiene ninguna ONU."));
            return buildSingleOnuPreview(new BuildSingleOnuPreviewCommand(usuarioId, sn));
        }
        Map<String, Object> usados = lotes.parametrosDeVistaPrevia(loteId);
        BatchFilters f = lote.filtros();
        return buildPreview(new BuildBatchCommand(usuarioId, new BatchFilters(f.marca(), f.oltCodigo(), f.tarjeta(), f.pon(),
                textos(usados.get(PERFILES_ORIGEN)), textos(usados.get(SNS)), f.tipo())));
    }

    private static List<String> textos(Object valor) {
        return valor instanceof List<?> lista ? lista.stream().map(String::valueOf).toList() : null;
    }

    private BatchView guardar(BatchMode modo, BatchFilters filtros, long usuarioId, List<ClassifiedOnu> clasificadas,
                              ParametersSnapshot snapshot) {
        Instant ahora = reloj.instant();
        Instant vence = ahora.plus(Duration.ofMinutes(entero(snapshot, "vigencia_vista_previa_minutos", 30)));
        List<ClassifiedOnu> ordenadas = new ArrayList<>(clasificadas);
        ordenadas.sort(Comparator.comparing((ClassifiedOnu c) -> c.grupo().ordinal())
                .thenComparing(ClassifiedOnu::oltCodigo, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ClassifiedOnu::tarjeta, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(ClassifiedOnu::pon)
                .thenComparingInt(ClassifiedOnu::onuId));
        Map<String, Object> congelados = new LinkedHashMap<>(snapshot.values());
        congelados.put(HUELLA, snapshot.fingerprint());
        if (filtros.perfilesOrigen() != null) {
            congelados.put(PERFILES_ORIGEN, filtros.perfilesOrigen());
        }
        if (filtros.sns() != null) {
            congelados.put(SNS, filtros.sns());
        }
        long id = transacciones.enTransaccion(() -> {
            BatchNumber numero = BatchNumber.of(ahora.atZone(ZoneOffset.UTC).getYear(), lotes.siguienteConsecutivo());
            return lotes.crearVistaPrevia(numero, modo, filtros, usuarioId, ahora, vence, congelados, ordenadas);
        });
        // Las que la OLT muestra ya con el perfil nuevo resuelven sus fallas anteriores (no se ofrecen para reintentar).
        List<String> yaCorregidas = ordenadas.stream().filter(c -> c.grupo() == ItemGroup.YA_TIENE_PERFIL).map(ClassifiedOnu::sn).toList();
        // Solo en lotes de perfil: en uno de VLAN, "ya la tiene" habla de la VLAN, no del perfil de las fallas anteriores.
        if (!yaCorregidas.isEmpty() && !filtros.esVlanGestion()) {
            ejecuciones.yaTienenElPerfil(id, usuarioId, yaCorregidas);
        }
        LoteDetalle lote = lotes.detalle(id).orElseThrow();
        Map<String, Object> peticion = new LinkedHashMap<>();
        peticion.put("marca", filtros.marca());
        peticion.put("olt", filtros.oltCodigo());
        peticion.put("tarjeta", filtros.tarjeta());
        peticion.put("pon", filtros.pon());
        peticion.put("tipo", filtros.tipo());
        auditar(AuditAction.VISTA_PREVIA_ARMADA, usuarioId, lote, peticion, "Vista previa " + lote.numero() + ": "
                + lote.contadores().get("entran") + " entran, " + lote.contadores().get("excluidas") + " quedan fuera, "
                + lote.contadores().get("yaTienenPerfil") + " ya tienen el perfil.");
        return lote.resumen();
    }

    // ---- Controlar

    @Override
    public BatchView confirm(ConfirmBatchCommand command) {
        boolean real = !command.simulacion();
        if (real) {
            motivoSoloSimulacion().ifPresent(motivo -> {
                throw ErrorDeNegocio.conflicto(motivo);
            });
        }
        LoteDetalle resultado = transacciones.enTransaccion(() -> {
            BatchStatus estado = estadoActual(command.loteId());
            if (estado == BatchStatus.CONFIRMADO || estado.isRunning()) {
                return lotes.detalle(command.loteId()).orElseThrow();
            }
            exigir(estado == BatchStatus.VISTA_PREVIA, "Este lote ya no se puede confirmar: está " + texto(estado) + ".");
            LoteDetalle lote = lotes.detalle(command.loteId()).orElseThrow();
            Instant ahora = reloj.instant();
            if (ahora.isAfter(lote.venceEn())) {
                throw ErrorDeNegocio.conflicto("La vista previa venció. Arma una nueva para ver los datos actuales.");
            }
            ParametersSnapshot actual = parametros.current();
            Map<String, Object> deLaVistaPrevia = lotes.parametrosDeVistaPrevia(lote.id());
            Object huella = deLaVistaPrevia.get(HUELLA);
            if (!actual.fingerprint().equals(huella)) {
                throw ErrorDeNegocio.conflicto("La configuración cambió después de armar la vista previa (RN-36). Arma una nueva.");
            }
            List<ScheduledOnu> entran = lotes.itemsQueEntran(lote.id());
            exigir(!entran.isEmpty(), "No hay ninguna ONU en el grupo \"entran\"; no hay nada que confirmar.");
            String version = procedimientoVersion.get();
            Map<String, Object> congelados = new LinkedHashMap<>(actual.values());
            congelados.put(HUELLA, actual.fingerprint());
            for (String clave : List.of(PERFILES_ORIGEN, SNS)) {
                if (deLaVistaPrevia.get(clave) != null) {
                    congelados.put(clave, deLaVistaPrevia.get(clave));
                }
            }
            lotes.confirmar(lote.id(), command.usuarioId(), command.simulacion(), ahora, congelados, version);
            ejecuciones.schedule(lote.id(), version, entran);
            LoteDetalle confirmado = lotes.detalle(lote.id()).orElseThrow();
            auditar(AuditAction.LOTE_CONFIRMADO, command.usuarioId(), confirmado, Map.of("simulacion", command.simulacion()),
                    "Lote " + lote.numero() + " confirmado" + (command.simulacion() ? " como simulación" : "") + " con "
                            + entran.size() + " ONUs.");
            return confirmado;
        });
        return resultado.resumen();
    }

    @Override
    public BatchView discard(DiscardBatchCommand command) {
        return transacciones.enTransaccion(() -> {
            BatchStatus estado = estadoActual(command.loteId());
            if (estado == BatchStatus.DESCARTADO) {
                return lotes.detalle(command.loteId()).orElseThrow().resumen();
            }
            exigir(estado == BatchStatus.VISTA_PREVIA, "Solo se descarta una vista previa; este lote está " + texto(estado) + ".");
            lotes.cambiarEstado(command.loteId(), BatchStatus.DESCARTADO, command.usuarioId(), "Descartada por el usuario.", reloj.instant());
            LoteDetalle lote = lotes.detalle(command.loteId()).orElseThrow();
            auditar(AuditAction.VISTA_PREVIA_DESCARTADA, command.usuarioId(), lote, Map.of(), "Vista previa " + lote.numero() + " descartada.");
            return lote.resumen();
        });
    }

    @Override
    public BatchView stop(StopBatchCommand command) {
        return transacciones.enTransaccion(() -> {
            BatchStatus estado = estadoActual(command.loteId());
            if (estado == BatchStatus.DETENIENDOSE || estado == BatchStatus.DETENIDO) {
                return lotes.detalle(command.loteId()).orElseThrow().resumen();
            }
            Instant ahora = reloj.instant();
            if (estado == BatchStatus.CONFIRMADO) {
                // Todavía no empezó ninguna ONU: se detiene de una vez.
                lotes.cambiarEstado(command.loteId(), BatchStatus.DETENIDO, command.usuarioId(), "Detenido antes de empezar.", ahora);
                LoteDetalle lote = lotes.detalle(command.loteId()).orElseThrow();
                auditar(AuditAction.LOTE_DETENIDO, command.usuarioId(), lote, Map.of(), "Lote " + lote.numero() + " detenido antes de empezar.");
                return lote.resumen();
            }
            exigir(estado == BatchStatus.EN_EJECUCION, "Este lote no está en ejecución: está " + texto(estado) + ".");
            lotes.cambiarEstado(command.loteId(), BatchStatus.DETENIENDOSE, command.usuarioId(), "Detenido por el usuario.", ahora);
            LoteDetalle lote = lotes.detalle(command.loteId()).orElseThrow();
            auditar(AuditAction.LOTE_DETENIENDOSE, command.usuarioId(), lote, Map.of(),
                    "Lote " + lote.numero() + " deteniéndose: la ONU en curso termina y no empieza ninguna más.");
            return lote.resumen();
        });
    }

    // ---- Consultar

    @Override
    public Optional<LoteDetalle> detalle(long loteId) {
        return lotes.detalle(loteId);
    }

    @Override
    public List<LoteDetalle> recientes(int limite) {
        return lotes.recientes(limite);
    }

    @Override
    public List<LoteDetalle> recientes(int limite, boolean conArchivados) {
        return lotes.recientes(limite, conArchivados);
    }

    @Override
    public List<LoteDetalle> recientes(int limite, boolean conArchivados, Long creadoPor) {
        return lotes.recientes(limite, conArchivados, creadoPor);
    }

    @Override
    public int archivarTerminados(Long usuarioId, String ipOrigen) {
        List<String> numeros = lotes.archivarTerminados(reloj.instant());
        auditoria.record(AuditEntry.builder(usuarioId == null ? ActorType.CONSOLA : ActorType.USUARIO, AuditAction.LOTES_ARCHIVADOS)
                .usuarioId(usuarioId).ipOrigen(ipOrigen).sistema("lotes").peticion(Map.of("cantidad", numeros.size()))
                .resultado("OK").mensaje(numeros.isEmpty() ? "No había lotes para archivar."
                        : numeros.size() + " lote(s) archivados (no se borró nada): " + String.join(", ", numeros)).build());
        return numeros.size();
    }

    @Override
    public List<ClassifiedOnu> vistaPrevia(long loteId) {
        return lotes.items(loteId);
    }

    @Override
    public Optional<String> motivoSoloSimulacion() {
        Object guardar = parametros.current().values().get("guardar_configuracion_olt");
        return guardar == null
                ? Optional.of("Todavía no se ha decidido si se guarda la configuración de la OLT (PC-36): por ahora solo se puede simular.")
                : Optional.empty();
    }

    @Override
    public int minutosEstimadosPorOnu() {
        return entero(parametros.current(), "tiempo_estimado_por_onu_minutos", 2);
    }

    // ----

    private BatchStatus estadoActual(long loteId) {
        return lotes.estadoBloqueando(loteId).orElseThrow(() -> ErrorDeNegocio.noEncontrado("No existe el lote " + loteId + "."));
    }

    private void auditar(AuditAction accion, long usuarioId, LoteDetalle lote, Map<String, Object> peticion, String mensaje) {
        auditoria.record(AuditEntry.builder(ActorType.USUARIO, accion)
                .usuarioId(usuarioId).loteId(lote.id()).sistema("lotes").peticion(peticion).resultado("OK").mensaje(mensaje).build());
    }

    private static void exigir(boolean condicion, String mensaje) {
        if (!condicion) {
            throw ErrorDeNegocio.conflicto(mensaje);
        }
    }

    private static int entero(ParametersSnapshot snapshot, String clave, int porDefecto) {
        return snapshot.values().get(clave) instanceof Number n ? n.intValue() : porDefecto;
    }

    private static String texto(BatchStatus estado) {
        return estado.name().toLowerCase().replace('_', ' ');
    }
}
