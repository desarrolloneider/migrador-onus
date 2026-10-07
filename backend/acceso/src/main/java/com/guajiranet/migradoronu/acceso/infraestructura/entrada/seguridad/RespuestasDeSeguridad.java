package com.guajiranet.migradoronu.acceso.infraestructura.entrada.seguridad;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Respuestas de Spring Security en Problem Details, con mensajes para la persona (RN-20). */
final class RespuestasDeSeguridad {

    private RespuestasDeSeguridad() {
    }

    static void sinSesion(HttpServletRequest request, HttpServletResponse response, AuthenticationException causa) throws IOException {
        escribir(response, 401, "Sin sesión", "Tu sesión terminó. Vuelve a entrar.");
    }

    static void denegado(HttpServletRequest request, HttpServletResponse response, AccessDeniedException causa) throws IOException {
        // Con sesión, la causa típica es el token CSRF: la página quedó abierta de una sesión anterior.
        escribir(response, 403, "Acción rechazada", "La página quedó desactualizada por seguridad. Recárgala e intenta de nuevo.");
    }

    static void escribir(HttpServletResponse response, int estado, String titulo, String detalle) throws IOException {
        response.setStatus(estado);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + titulo + "\",\"status\":" + estado
                + ",\"detail\":\"" + detalle + "\"}");
    }
}
