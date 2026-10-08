package com.guajiranet.migradoronu.acceso.infraestructura.entrada.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.guajiranet.migradoronu.acceso.dominio.ResultadoAutenticacion;
import com.guajiranet.migradoronu.acceso.dominio.ResultadoAutenticacion.Motivo;
import com.guajiranet.migradoronu.acceso.dominio.Usuario;
import com.guajiranet.migradoronu.acceso.puertos.entrada.AuthenticatePort;
import com.guajiranet.migradoronu.acceso.puertos.entrada.OwnCredentialsPort;
import com.guajiranet.migradoronu.acceso.puertos.entrada.QueryUsersPort;
import com.guajiranet.migradoronu.comun.errores.ErrorDeNegocio;
import com.guajiranet.migradoronu.comun.seguridad.UsuarioAutenticado;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Inicio y cierre de sesión (ESP-01). La sesión vive en el servidor con una cookie HttpOnly; el navegador no guarda
 * ni la contraseña ni un token (arquitectura 11.1).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    public record LoginRequest(String usuario, String contrasena) {
    }

    /** @param modulos los módulos que puede usar (todos si es administrador); la pantalla muestra solo esos */
    public record UsuarioSesion(long id, String usuario, String nombre, boolean debeCambiarClave, boolean administrador,
                                List<String> modulos) {
    }

    public record CambiarCredencialesRequest(String usuarioNuevo, String claveActual, String claveNueva) {
    }

    private final AuthenticatePort autenticacion;
    private final SecurityContextRepository contextos;
    private final SecurityContextHolderStrategy estrategia = SecurityContextHolder.getContextHolderStrategy();

    private final OwnCredentialsPort credenciales;

    private final QueryUsersPort usuarios;

    public AuthController(AuthenticatePort autenticacion, SecurityContextRepository contextos, OwnCredentialsPort credenciales,
                          QueryUsersPort usuarios) {
        this.autenticacion = autenticacion;
        this.contextos = contextos;
        this.credenciales = credenciales;
        this.usuarios = usuarios;
    }

    private UsuarioSesion de(UsuarioAutenticado u) {
        var permisos = usuarios.permisos(u.id());
        return new UsuarioSesion(u.id(), u.usuario(), u.nombre(), credenciales.debeCambiarClave(u.id()), permisos.administrador(),
                permisos.efectivos().stream().map(Enum::name).sorted().toList());
    }

    /** Cambia la contraseña propia y, si se pide, el nombre de usuario. La sesión sigue abierta con el nombre nuevo. */
    @PostMapping("/cambiar-credenciales")
    public UsuarioSesion cambiarCredenciales(@RequestBody CambiarCredencialesRequest cuerpo, @AuthenticationPrincipal UsuarioAutenticado usuario,
                                             HttpServletRequest request, HttpServletResponse response) {
        if (usuario == null) {
            throw new ErrorDeNegocio(ErrorDeNegocio.Tipo.SIN_SESION, "Sin sesión.");
        }
        Usuario u = credenciales.cambiar(usuario.id(), cuerpo.usuarioNuevo(),
                cuerpo.claveActual() == null ? null : cuerpo.claveActual().toCharArray(),
                cuerpo.claveNueva() == null ? null : cuerpo.claveNueva().toCharArray(), request.getRemoteAddr());
        UsuarioAutenticado principal = new UsuarioAutenticado(u.id(), u.usuario(), u.nombre());
        SecurityContext contexto = estrategia.createEmptyContext();
        contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
        estrategia.setContext(contexto);
        contextos.saveContext(contexto, request, response);
        return de(principal);
    }

    @PostMapping("/login")
    public UsuarioSesion iniciarSesion(@RequestBody LoginRequest cuerpo, HttpServletRequest request, HttpServletResponse response) {
        ResultadoAutenticacion resultado = autenticacion.autenticar(cuerpo.usuario(), cuerpo.contrasena(), request.getRemoteAddr());
        return switch (resultado) {
            case ResultadoAutenticacion.Exitosa exitosa -> abrirSesion(exitosa, request, response);
            case ResultadoAutenticacion.Rechazada rechazada when rechazada.motivo() == Motivo.BLOQUEADO ->
                    throw new ErrorDeNegocio(ErrorDeNegocio.Tipo.BLOQUEADO,
                            "Usuario bloqueado por intentos fallidos. Intenta de nuevo en " + rechazada.minutosDeBloqueo() + " minutos.");
            case ResultadoAutenticacion.Rechazada ignorada ->
                    throw new ErrorDeNegocio(ErrorDeNegocio.Tipo.SIN_SESION, "Usuario o contraseña incorrectos.");
        };
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> cerrarSesion(@AuthenticationPrincipal UsuarioAutenticado usuario, HttpServletRequest request) {
        if (usuario != null) {
            autenticacion.registrarCierre(usuario.id(), request.getRemoteAddr());
        }
        HttpSession sesion = request.getSession(false);
        if (sesion != null) {
            sesion.invalidate();
        }
        estrategia.clearContext();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sesion")
    public UsuarioSesion sesion(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        if (usuario == null) {
            throw new ErrorDeNegocio(ErrorDeNegocio.Tipo.SIN_SESION, "Sin sesión.");
        }
        return de(usuario);
    }

    private UsuarioSesion abrirSesion(ResultadoAutenticacion.Exitosa exitosa, HttpServletRequest request, HttpServletResponse response) {
        // Fijación de sesión: si ya había una sesión anónima, se le cambia el id al autenticar.
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        Usuario u = exitosa.usuario();
        UsuarioAutenticado principal = new UsuarioAutenticado(u.id(), u.usuario(), u.nombre());
        SecurityContext contexto = estrategia.createEmptyContext();
        contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
        estrategia.setContext(contexto);
        contextos.saveContext(contexto, request, response);
        // Cierre por inactividad según el parámetro vigente (spec 7.5). Los lotes siguen corriendo.
        request.getSession().setMaxInactiveInterval((int) exitosa.inactividad().toSeconds());
        return de(principal);
    }
}
