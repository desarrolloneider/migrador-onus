package com.guajiranet.migradoronu.acceso.infraestructura.entrada.seguridad;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter; 
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfFilter;

import com.guajiranet.migradoronu.acceso.puertos.entrada.OwnCredentialsPort;
import com.guajiranet.migradoronu.acceso.puertos.entrada.QueryUsersPort;

/**
 * Seguridad del proceso web (arquitectura 11.1): sesión en el servidor, CSRF para la SPA con la cookie XSRF-TOKEN,
 * sin formulario de Spring ni autenticación básica. Toda la API exige sesión salvo el inicio de sesión (DEC-05: sin
 * roles en el MVP).
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SeguridadConfig {

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SecurityFilterChain cadenaDeSeguridad(HttpSecurity http, SecurityContextRepository contextos, QueryUsersPort usuarios,
                                       OwnCredentialsPort credenciales) throws Exception {
        http
                .authorizeHttpRequests(reglas -> reglas
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/sesion").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info", "/error").permitAll()
                        .anyRequest().authenticated())
                .securityContext(contexto -> contexto.securityContextRepository(contextos))
                .csrf(csrf -> csrf.spa())
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .addFilterBefore(new UsuarioActivoFilter(usuarios, credenciales), AuthorizationFilter.class)
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(RespuestasDeSeguridad::sinSesion)
                        .accessDeniedHandler(RespuestasDeSeguridad::denegado))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
