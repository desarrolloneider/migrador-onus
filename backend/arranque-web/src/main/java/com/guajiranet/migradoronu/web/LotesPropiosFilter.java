package com.guajiranet.migradoronu.web;

import java.io.IOException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.guajiranet.migradoronu.comun.seguridad.UsuarioAutenticado;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Cada usuario solo ve y maneja sus propios lotes; el administrador, todos (pedido del 22 de octubre). Aquí se cuidan las
 * direcciones que nombran un lote o una ejecución ({@code /batches/{id}/...}, {@code /onu-runs/{id}/...}); las listas se
 * filtran en sus controladores. Un lote de otro responde 404, igual que uno que no existe.
 */
@Component
@ConditionalOnWebApplication
public class LotesPropiosFilter extends OncePerRequestFilter {

    private static final Pattern LOTE = Pattern.compile("^/api/v1/batches/(\\d+)(/.*)?$");
    private static final Pattern EJECUCION = Pattern.compile("^/api/v1/onu-runs/(\\d+)(/.*)?$");

    private final JdbcClient jdbc;

    public LotesPropiosFilter(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain cadena)
            throws ServletException, IOException {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion != null && autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario
                && !UsuarioAutenticado.esAdministrador(request.getAttribute(UsuarioAutenticado.ATRIBUTO_ADMINISTRADOR))) {
            Optional<Long> dueno = duenoDe(request.getRequestURI());
            if (dueno != null && (dueno.isEmpty() || dueno.get() != usuario.id())) {
                response.setStatus(404);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"title\":\"No encontrado\",\"detail\":\"No existe ese lote, o es de otro usuario.\"}");
                return;
            }
        }
        cadena.doFilter(request, response);
    }

    /** Quién armó el lote que nombra la dirección; null si la dirección no nombra un lote ni una ejecución. */
    private Optional<Long> duenoDe(String ruta) {
        Matcher m = LOTE.matcher(ruta);
        if (m.matches()) {
            return jdbc.sql("SELECT creado_por FROM lotes WHERE id = ?").param(Long.parseLong(m.group(1))).query(Long.class).optional();
        }
        m = EJECUCION.matcher(ruta);
        if (m.matches()) {
            return jdbc.sql("SELECT l.creado_por FROM ejecuciones_onu e JOIN lotes l ON l.id = e.lote_id WHERE e.id = ?")
                    .param(Long.parseLong(m.group(1))).query(Long.class).optional();
        }
        return null;
    }
}
