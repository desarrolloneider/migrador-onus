package com.guajiranet.migradoronu.configuracion.infraestructura.configuracion;

import java.time.Clock;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.guajiranet.migradoronu.comun.puertos.AuditTrailPort;
import com.guajiranet.migradoronu.configuracion.aplicacion.ConfiguracionUseCase;
import com.guajiranet.migradoronu.configuracion.infraestructura.salida.persistencia.JdbcOltSettingsRepositoryAdapter;
import com.guajiranet.migradoronu.configuracion.infraestructura.salida.persistencia.JdbcParametersRepositoryAdapter;
import com.guajiranet.migradoronu.configuracion.puertos.salida.ActiveBatchQueryPort;

/** Arma el módulo configuracion. Un solo caso de uso implementa los tres puertos de entrada. */
@Configuration(proxyBeanMethods = false)
public class ConfiguracionConfig {

    @Bean
    ConfiguracionUseCase configuracionUseCase(DataSource dataSource, ActiveBatchQueryPort lotesActivos, AuditTrailPort auditoria,
                                              Clock reloj) {
        return new ConfiguracionUseCase(new JdbcParametersRepositoryAdapter(dataSource),
                new JdbcOltSettingsRepositoryAdapter(dataSource), lotesActivos, auditoria, reloj);
    }
}
