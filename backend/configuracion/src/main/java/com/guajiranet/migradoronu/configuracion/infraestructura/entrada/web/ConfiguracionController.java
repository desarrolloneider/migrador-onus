package com.guajiranet.migradoronu.configuracion.infraestructura.entrada.web;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.guajiranet.migradoronu.comun.puertos.UserNamesPort;
import com.guajiranet.migradoronu.comun.seguridad.UsuarioAutenticado;
import com.guajiranet.migradoronu.configuracion.dominio.DefinicionParametro;
import com.guajiranet.migradoronu.configuracion.dominio.OltSettings;
import com.guajiranet.migradoronu.configuracion.dominio.Parametro;
import com.guajiranet.migradoronu.configuracion.puertos.entrada.ConfigureParametersPort;
import com.guajiranet.migradoronu.configuracion.puertos.entrada.ManageOltSettingsPort;
import com.guajiranet.migradoronu.configuracion.puertos.entrada.QueryParametersPort;

/** ESP-15: parámetros, OLT y tarjetas permitidas. */
@RestController
@RequestMapping("/api/v1")
public class ConfiguracionController {

    public record ParametroDto(String clave, String nombre, String grupo, Object valor, String unidad, String descripcion,
                               String referencia, String tipo, List<DefinicionParametro.Opcion> opciones, Integer minimo,
                               Integer maximo, boolean editable, String actualizadoPor, Instant actualizadoEn) {
    }

    public record ConfiguracionDto(List<ParametroDto> parametros, String bloqueadaPorLote) {
    }

    public record OltDto(long id, String codigo, String nombre, String direccion, Integer puerto, String protocolo,
                         List<String> tarjetasPermitidas, boolean accesoConfigurado,
                         String huellaSsh) {
    }

    public record CambiarAccesoRequest(String direccion, Integer puerto, String protocolo) {
    }

    public record FijarHuellaRequest(String huella) {
    }

    public record CambiarParametroRequest(Object valor) {
    }

    public record CambiarTarjetasRequest(List<String> tarjetas) {
    }

    private final QueryParametersPort consultas;
    private final ConfigureParametersPort parametros;
    private final ManageOltSettingsPort olts;
    private final UserNamesPort nombres;

    public ConfiguracionController(QueryParametersPort consultas, ConfigureParametersPort parametros, ManageOltSettingsPort olts,
                                   UserNamesPort nombres) {
        this.consultas = consultas;
        this.parametros = parametros;
        this.olts = olts;
        this.nombres = nombres;
    }

    @GetMapping("/settings")
    public ConfiguracionDto consultar() {
        List<Parametro> lista = consultas.parametros();
        Set<Long> ids = new HashSet<>();
        lista.stream().map(Parametro::actualizadoPor).filter(Objects::nonNull).forEach(ids::add);
        Map<Long, String> quien = ids.isEmpty() ? Map.of() : nombres.nombres(ids);
        return new ConfiguracionDto(lista.stream().map(p -> dto(p, quien)).toList(), consultas.loteQueBloquea().orElse(null));
    }

    @PutMapping("/settings/{clave}")
    public ParametroDto cambiar(@PathVariable String clave, @RequestBody CambiarParametroRequest cuerpo,
                                @AuthenticationPrincipal UsuarioAutenticado usuario, HttpServletRequest request) {
        Parametro p = parametros.cambiar(clave, cuerpo == null ? null : cuerpo.valor(), usuario.id(), request.getRemoteAddr());
        return dto(p, Map.of(usuario.id(), usuario.nombre()));
    }

    @GetMapping("/olts")
    public List<OltDto> listarOlts() {
        return consultas.olts().stream().map(this::dto).toList();
    }

    @PutMapping("/olts/{oltId}/access")
    public OltDto cambiarAcceso(@PathVariable long oltId, @RequestBody CambiarAccesoRequest cuerpo,
                                @AuthenticationPrincipal UsuarioAutenticado usuario, HttpServletRequest request) {
        return dto(olts.cambiarAcceso(oltId, cuerpo.direccion(), cuerpo.puerto(), cuerpo.protocolo(), usuario.id(),
                request.getRemoteAddr()));
    }

    @PutMapping("/olts/{oltId}/fingerprint")
    public void fijarHuella(@PathVariable long oltId, @RequestBody FijarHuellaRequest cuerpo,
                            @AuthenticationPrincipal UsuarioAutenticado usuario, HttpServletRequest request) {
        olts.fijarHuella(oltId, cuerpo == null ? null : cuerpo.huella(), usuario.id(), request.getRemoteAddr());
    }

    @PutMapping("/olts/{oltId}/boards")
    public OltDto cambiarTarjetas(@PathVariable long oltId, @RequestBody CambiarTarjetasRequest cuerpo,
                                  @AuthenticationPrincipal UsuarioAutenticado usuario, HttpServletRequest request) {
        List<String> tarjetas = cuerpo == null || cuerpo.tarjetas() == null ? List.of() : cuerpo.tarjetas();
        return dto(olts.cambiarTarjetas(oltId, tarjetas, usuario.id(), request.getRemoteAddr()));
    }

    private static ParametroDto dto(Parametro p, Map<Long, String> quien) {
        DefinicionParametro d = p.definicion();
        String actualizadoPor = p.actualizadoPor() == null ? null
                : quien.getOrDefault(p.actualizadoPor(), "Usuario " + p.actualizadoPor());
        return new ParametroDto(d.clave(), d.nombre(), d.grupo(), p.valor(), d.unidad(), d.descripcion(), d.referencia(),
                d.tipo().name(), d.opciones(), d.minimo(), d.maximo(), d.editable(), actualizadoPor, p.actualizadoEn());
    }

    private OltDto dto(OltSettings o) {
        return new OltDto(o.id(), o.codigo(), o.nombre(), o.direccion(), o.puerto(),
                o.protocolo() == null ? null : o.protocolo().name(), List.copyOf(o.tarjetas().values()),
                o.consoleAccessConfigured(), consultas.huellaSsh(o.codigo()).orElse(null));
    }
}
