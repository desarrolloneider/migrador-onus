package com.guajiranet.migradoronu.acceso.infraestructura.entrada.seguridad;

import java.io.IOException;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * El token CSRF se carga de forma diferida; tocarlo en cada petición hace que la cookie XSRF-TOKEN exista desde la
 * primera respuesta, y así Angular puede mandarlo en X-XSRF-TOKEN desde el inicio de sesión.
 */
final class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain cadena)
            throws ServletException, IOException {
        if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token) {
            token.getToken();
        }
        cadena.doFilter(request, response);
    }
}
