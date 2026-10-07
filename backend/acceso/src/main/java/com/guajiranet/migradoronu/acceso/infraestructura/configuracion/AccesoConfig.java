package com.guajiranet.migradoronu.acceso.infraestructura.configuracion;

import java.time.Clock;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.guajiranet.migradoronu.acceso.aplicacion.AdministrarUsuariosUseCase;
import com.guajiranet.migradoronu.acceso.aplicacion.AutenticarUseCase;
import com.guajiranet.migradoronu.acceso.aplicacion.ConsultarUsuariosUseCase;
import com.guajiranet.migradoronu.acceso.infraestructura.salida.persistencia.JdbcUserRepositoryAdapter;
import com.guajiranet.migradoronu.acceso.infraestructura.salida.seguridad.Argon2PasswordHasherAdapter;
import com.guajiranet.migradoronu.acceso.puertos.entrada.AuthenticatePort;
import com.guajiranet.migradoronu.acceso.puertos.entrada.ManageUsersPort;
import com.guajiranet.migradoronu.acceso.puertos.entrada.QueryUsersPort;
import com.guajiranet.migradoronu.acceso.puertos.salida.AccessPolicyPort;
import com.guajiranet.migradoronu.acceso.puertos.salida.PasswordHasherPort;
import com.guajiranet.migradoronu.acceso.puertos.salida.UserRepositoryPort;
import com.guajiranet.migradoronu.comun.puertos.AuditTrailPort;

/** Arma el módulo acceso: los casos de uso son Java simple y aquí se conectan con sus adaptadores. */
@Configuration(proxyBeanMethods = false)
public class AccesoConfig {

    @Bean
    UserRepositoryPort userRepository(DataSource dataSource) {
        return new JdbcUserRepositoryAdapter(dataSource);
    }

    @Bean
    PasswordHasherPort passwordHasher() {
        return new Argon2PasswordHasherAdapter();
    }

    @Bean
    AuthenticatePort authenticate(UserRepositoryPort usuarios, PasswordHasherPort huellas, AccessPolicyPort politicas,
                                  AuditTrailPort auditoria, Clock reloj) {
        return new AutenticarUseCase(usuarios, huellas, politicas, auditoria, reloj);
    }

    @Bean
    ManageUsersPort manageUsers(UserRepositoryPort usuarios, PasswordHasherPort huellas, AuditTrailPort auditoria, Clock reloj) {
        return new AdministrarUsuariosUseCase(usuarios, huellas, auditoria, reloj);
    }

    @Bean
    QueryUsersPort queryUsers(UserRepositoryPort usuarios) {
        return new ConsultarUsuariosUseCase(usuarios);
    }
}
