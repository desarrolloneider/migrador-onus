package com.guajiranet.migradoronu.acceso.infraestructura.entrada.seguridad;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.guajiranet.migradoronu.acceso.puertos.entrada.OwnCredentialsPort;
import com.guajiranet.migradoronu.acceso.puertos.entrada.QueryUsersPort;
import com.guajiranet.migradoronu.comun.seguridad.UsuarioAutenticado;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * ESP-02: "dado que desactivo un usuario con sesión abierta, cuando intenta cualquier acción, entonces se le niega".
 * En cada petición con sesión se confirma que el usuario sigue activo; si no, se cierra su sesión.
 */
final class UsuarioActivoFilter extends OncePerRequestFilter {

    private final QueryUsersPort usuarios;
    private final OwnCredentialsPort credenciales;

    UsuarioActivoFilter(QueryUsersPort usuarios, OwnCredentialsPort credenciales) {
        this.usuarios = usuarios;
        this.credenciales = credenciales;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain cadena)
            throws ServletException, IOException {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion != null && autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario && !usuarios.activo(usuario.id())) {
            HttpSession sesion = request.getSession(false);
            if (sesion != null) {
                sesion.invalidate();
            }
            SecurityContextHolder.clearContext();
            RespuestasDeSeguridad.escribir(response, 401, "Sin sesión", "Tu usuario fue desactivado. Habla con el administrador de la herramienta.");
            return;
        }
        // Con la contraseña inicial solo se puede cambiar la contraseña o cerrar sesión.
        if (autenticacion != null && autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario
                && !request.getRequestURI().startsWith("/api/v1/auth/") && request.getRequestURI().startsWith("/api/")
                && credenciales.debeCambiarClave(usuario.id())) {
            RespuestasDeSeguridad.escribir(response, 403, "Cambia tu contraseña", "Antes de continuar debes cambiar la contraseña inicial.");
            return;
        }
        // Módulos: cada dirección exige el suyo (ReglasDePermiso). Se revisa en cada petición, así un cambio de permisos
        // aplica de inmediato sin cerrar la sesión.
        if (autenticacion != null && autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario) {
            String ruta = request.getRequestURI();
            var exigencia = ReglasDePermiso.para(request.getMethod(), ruta);
            if (exigencia.isPresent() && !exigencia.get().cumple(usuarios.permisos(usuario.id()))) {
                RespuestasDeSeguridad.escribir(response, 403, "Sin permiso",
                        "No tienes permiso para " + exigencia.get().descripcion() + ". Pídeselo a un administrador.");
                return;
            }
        }
        cadena.doFilter(request, response);
    }
}
