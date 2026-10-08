package com.guajiranet.migradoronu.acceso.infraestructura.entrada.web;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.guajiranet.migradoronu.acceso.aplicacion.GestionUsuariosUseCase;
import com.guajiranet.migradoronu.acceso.aplicacion.GestionUsuariosUseCase.ConClaveTemporal;
import com.guajiranet.migradoronu.acceso.aplicacion.GestionUsuariosUseCase.Ficha;
import com.guajiranet.migradoronu.acceso.dominio.Modulo;
import com.guajiranet.migradoronu.comun.errores.ErrorDeNegocio;
import com.guajiranet.migradoronu.comun.seguridad.UsuarioAutenticado;

/** Gestión de usuarios, solo para administradores (lo exige ReglasDePermiso en cada petición). */
@RestController
@RequestMapping("/api/v1/users")
public class UsuariosController {

    public record ModuloDto(String codigo, String nombre, String descripcion) {
    }

    public record UsuarioRequest(String usuario, String nombre, boolean administrador, List<String> modulos) {
    }

    public record ConClaveDto(Ficha usuario, String claveTemporal) {
    }

    private final GestionUsuariosUseCase gestion;

    public UsuariosController(GestionUsuariosUseCase gestion) {
        this.gestion = gestion;
    }

    @GetMapping("/modules")
    public List<ModuloDto> modulos() {
        return Arrays.stream(Modulo.values()).map(m -> new ModuloDto(m.name(), m.nombre(), m.descripcion())).toList();
    }

    @GetMapping
    public List<Ficha> listar() {
        return gestion.listar();
    }

    @GetMapping("/{id}")
    public Ficha ficha(@PathVariable long id) {
        return gestion.ficha(id);
    }

    @PostMapping
    public ConClaveDto crear(@RequestBody UsuarioRequest cuerpo, @AuthenticationPrincipal UsuarioAutenticado quien, HttpServletRequest request) {
        ConClaveTemporal r = gestion.crear(quien.id(), request.getRemoteAddr(), cuerpo.usuario(), cuerpo.nombre(), cuerpo.administrador(),
                modulos(cuerpo.modulos()));
        return new ConClaveDto(r.ficha(), r.claveTemporal());
    }

    @PutMapping("/{id}")
    public Ficha actualizar(@PathVariable long id, @RequestBody UsuarioRequest cuerpo, @AuthenticationPrincipal UsuarioAutenticado quien,
                            HttpServletRequest request) {
        return gestion.actualizar(quien.id(), request.getRemoteAddr(), id, cuerpo.nombre(), cuerpo.administrador(), modulos(cuerpo.modulos()));
    }

    @PostMapping("/{id}/deactivate")
    public Ficha desactivar(@PathVariable long id, @AuthenticationPrincipal UsuarioAutenticado quien, HttpServletRequest request) {
        return gestion.desactivar(quien.id(), request.getRemoteAddr(), id);
    }

    @PostMapping("/{id}/reactivate")
    public Ficha reactivar(@PathVariable long id, @AuthenticationPrincipal UsuarioAutenticado quien, HttpServletRequest request) {
        return gestion.reactivar(quien.id(), request.getRemoteAddr(), id);
    }

    @PostMapping("/{id}/reset-password")
    public ConClaveDto restablecer(@PathVariable long id, @AuthenticationPrincipal UsuarioAutenticado quien, HttpServletRequest request) {
        ConClaveTemporal r = gestion.restablecerClave(quien.id(), request.getRemoteAddr(), id);
        return new ConClaveDto(r.ficha(), r.claveTemporal());
    }

    private static Set<Modulo> modulos(List<String> codigos) {
        Set<Modulo> modulos = EnumSet.noneOf(Modulo.class);
        for (String c : codigos == null ? List.<String>of() : codigos) {
            try {
                modulos.add(Modulo.valueOf(c));
            } catch (IllegalArgumentException e) {
                throw ErrorDeNegocio.datosInvalidos("No existe el módulo " + c + ".");
            }
        }
        return modulos;
    }
}
