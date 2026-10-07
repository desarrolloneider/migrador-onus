package com.guajiranet.migradoronu.configuracion.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.guajiranet.migradoronu.comun.errores.ErrorDeNegocio;
import com.guajiranet.migradoronu.comun.puertos.ActorType;
import com.guajiranet.migradoronu.comun.puertos.AuditAction;
import com.guajiranet.migradoronu.comun.puertos.AuditEntry;
import com.guajiranet.migradoronu.comun.puertos.AuditTrailPort;
import com.guajiranet.migradoronu.configuracion.dominio.AllowedBoards;
import com.guajiranet.migradoronu.configuracion.dominio.CatalogoDeParametros;
import com.guajiranet.migradoronu.configuracion.dominio.DefinicionParametro;
import com.guajiranet.migradoronu.configuracion.dominio.OltSettings;
import com.guajiranet.migradoronu.configuracion.dominio.Parametro;
import com.guajiranet.migradoronu.configuracion.dominio.ValorGuardado;
import com.guajiranet.migradoronu.configuracion.puertos.entrada.ConfigureParametersPort;
import com.guajiranet.migradoronu.configuracion.puertos.entrada.ManageOltSettingsPort;
import com.guajiranet.migradoronu.configuracion.puertos.entrada.QueryParametersPort;
import com.guajiranet.migradoronu.configuracion.puertos.salida.ActiveBatchQueryPort;
import com.guajiranet.migradoronu.configuracion.puertos.salida.OltSettingsRepositoryPort;
import com.guajiranet.migradoronu.configuracion.puertos.salida.ParametersRepositoryPort;

/** Parámetros, OLT y tarjetas sin tocar el programa (ESP-15, RNF-11). */
public class ConfiguracionUseCase implements QueryParametersPort, ConfigureParametersPort, ManageOltSettingsPort {

    private final ParametersRepositoryPort parametros;
    private final OltSettingsRepositoryPort olts;
    private final ActiveBatchQueryPort lotesActivos;
    private final AuditTrailPort auditoria;
    private final Clock reloj;

    public ConfiguracionUseCase(ParametersRepositoryPort parametros, OltSettingsRepositoryPort olts, ActiveBatchQueryPort lotesActivos,
                                AuditTrailPort auditoria, Clock reloj) {
        this.parametros = Objects.requireNonNull(parametros, "parametros");
        this.olts = Objects.requireNonNull(olts, "olts");
        this.lotesActivos = Objects.requireNonNull(lotesActivos, "lotesActivos");
        this.auditoria = Objects.requireNonNull(auditoria, "auditoria");
        this.reloj = Objects.requireNonNull(reloj, "reloj");
    }

    @Override
    public ParametersView currentParameters() {
        Map<String, Object> valores = new LinkedHashMap<>();
        parametros.todos().forEach((clave, guardado) -> valores.put(clave, guardado.valor()));
        return new ParametersView(valores, parametros.revision());
    }

    @Override
    public List<Parametro> parametros() {
        Map<String, ValorGuardado> guardados = parametros.todos();
        return CatalogoDeParametros.TODOS.stream()
                .map(d -> {
                    ValorGuardado g = guardados.get(d.clave());
                    return g == null ? new Parametro(d, null, null, null) : new Parametro(d, g.valor(), g.actualizadoPor(), g.actualizadoEn());
                })
                .toList();
    }

    @Override
    public Optional<OltSettings> oltSettings(String oltCodigo) {
        return olts.porCodigo(oltCodigo);
    }

    @Override
    public List<OltSettings> olts() {
        return olts.listar();
    }

    @Override
    public Optional<String> loteQueBloquea() {
        return lotesActivos.runningBatchNumber();
    }

    @Override
    public Parametro cambiar(String clave, Object valor, long usuarioId, String ipOrigen) {
        DefinicionParametro definicion = CatalogoDeParametros.buscar(clave)
                .orElseThrow(() -> ErrorDeNegocio.noEncontrado("No existe el parámetro " + clave + "."));
        if (!definicion.editable()) {
            throw ErrorDeNegocio.datosInvalidos(definicion.nombre() + " no se cambia desde la pantalla.");
        }
        exigirSinLoteEnEjecucion();
        Object nuevo = definicion.validar(valor);
        Object anterior = Optional.ofNullable(parametros.todos().get(clave)).map(ValorGuardado::valor).orElse(null);
        Instant ahora = reloj.instant();
        parametros.guardar(clave, nuevo, usuarioId, ahora);
        Map<String, Object> peticion = new LinkedHashMap<>();
        peticion.put("parametro", clave);
        peticion.put("anterior", anterior);
        peticion.put("nuevo", nuevo);
        auditoria.record(AuditEntry.builder(ActorType.USUARIO, AuditAction.PARAMETRO_CAMBIADO)
                .usuarioId(usuarioId).ipOrigen(ipOrigen).sistema("configuracion").peticion(peticion).resultado("OK")
                .mensaje(definicion.nombre() + ": " + texto(anterior) + " → " + texto(nuevo))
                .build());
        return new Parametro(definicion, nuevo, usuarioId, ahora);
    }

    @Override
    public OltSettings cambiarTarjetas(long oltId, Collection<String> tarjetas, long usuarioId, String ipOrigen) {
        OltSettings olt = olts.porId(oltId).orElseThrow(() -> ErrorDeNegocio.noEncontrado("Esa OLT no está registrada."));
        exigirSinLoteEnEjecucion();
        AllowedBoards nuevas = new AllowedBoards(new HashSet<>(tarjetas == null ? List.of() : tarjetas));
        Instant ahora = reloj.instant();
        olts.guardarTarjetas(oltId, nuevas, usuarioId, ahora);
        Map<String, Object> peticion = new LinkedHashMap<>();
        peticion.put("anterior", List.copyOf(olt.tarjetas().values()));
        peticion.put("nuevo", List.copyOf(nuevas.values()));
        auditoria.record(AuditEntry.builder(ActorType.USUARIO, AuditAction.TARJETAS_OLT_CAMBIADAS)
                .usuarioId(usuarioId).ipOrigen(ipOrigen).sistema("OLT " + olt.codigo()).peticion(peticion).resultado("OK")
                .mensaje("Tarjetas de " + olt.codigo() + ": " + lista(olt.tarjetas()) + " → " + lista(nuevas))
                .build());
        return olt.conTarjetas(nuevas);
    }

    private void exigirSinLoteEnEjecucion() {
        lotesActivos.runningBatchNumber().ifPresent(numero -> {
            throw ErrorDeNegocio.conflicto("No se puede cambiar la configuración mientras el lote " + numero + " está en ejecución.");
        });
    }

    private static String texto(Object valor) {
        return valor == null ? "sin valor" : String.valueOf(valor);
    }

    private static String lista(AllowedBoards tarjetas) {
        return tarjetas.values().isEmpty() ? "ninguna" : String.join(", ", tarjetas.values());
    }
}
