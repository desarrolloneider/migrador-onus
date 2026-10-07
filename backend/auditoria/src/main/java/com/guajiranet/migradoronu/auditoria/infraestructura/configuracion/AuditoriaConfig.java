package com.guajiranet.migradoronu.auditoria.infraestructura.configuracion;

import java.nio.file.Path;
import java.time.Clock;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.transaction.PlatformTransactionManager;

import com.guajiranet.migradoronu.auditoria.aplicacion.AnchorAuditUseCase;
import com.guajiranet.migradoronu.auditoria.aplicacion.QueryAuditUseCase;
import com.guajiranet.migradoronu.auditoria.aplicacion.RecordAuditEventUseCase;
import com.guajiranet.migradoronu.auditoria.aplicacion.VerifyAuditIntegrityUseCase;
import com.guajiranet.migradoronu.auditoria.infraestructura.entrada.programacion.DailyAuditAnchorJob;
import com.guajiranet.migradoronu.auditoria.infraestructura.salida.archivo.FileAuditAnchorAdapter;
import com.guajiranet.migradoronu.auditoria.infraestructura.salida.persistencia.JdbcAuditRepositoryAdapter;
import com.guajiranet.migradoronu.auditoria.infraestructura.salida.secretos.EnvironmentKnownSecretsAdapter;
import com.guajiranet.migradoronu.auditoria.puertos.entrada.AnchorAuditPort;
import com.guajiranet.migradoronu.auditoria.puertos.entrada.QueryAuditPort;
import com.guajiranet.migradoronu.auditoria.puertos.entrada.RecordAuditEventPort;
import com.guajiranet.migradoronu.auditoria.puertos.entrada.VerifyAuditIntegrityPort;
import com.guajiranet.migradoronu.auditoria.puertos.salida.AuditAnchorPort;
import com.guajiranet.migradoronu.auditoria.puertos.salida.AuditRepositoryPort;
import com.guajiranet.migradoronu.auditoria.puertos.salida.KnownSecretsPort;

/**
 * Arma el módulo: los casos de uso son Java simple y aquí se conectan con sus adaptadores. Es la única clase del
 * módulo que Spring necesita encontrar.
 */
@Configuration(proxyBeanMethods = false)
public class AuditoriaConfig {

    @Bean
    AuditRepositoryPort auditRepository(DataSource dataSource, PlatformTransactionManager transactionManager) {
        return new JdbcAuditRepositoryAdapter(dataSource, transactionManager);
    }

    @Bean
    AuditAnchorPort auditAnchors(@Value("${migrador.auditoria.anclas.directorio:anclas}") Path directory) {
        return new FileAuditAnchorAdapter(directory);
    }

    @Bean
    KnownSecretsPort knownSecrets(ConfigurableEnvironment environment) {
        return new EnvironmentKnownSecretsAdapter(environment);
    }

    @Bean
    RecordAuditEventPort recordAuditEvent(AuditRepositoryPort repository, KnownSecretsPort knownSecrets, Clock clock) {
        return new RecordAuditEventUseCase(repository, knownSecrets, clock);
    }

    @Bean
    VerifyAuditIntegrityPort verifyAuditIntegrity(AuditRepositoryPort repository, AuditAnchorPort anchors,
                                                  RecordAuditEventPort recordAuditEvent) {
        return new VerifyAuditIntegrityUseCase(repository, anchors, recordAuditEvent);
    }

    @Bean
    AnchorAuditPort anchorAudit(AuditRepositoryPort repository, AuditAnchorPort anchors, Clock clock) {
        return new AnchorAuditUseCase(repository, anchors, clock);
    }

    @Bean
    QueryAuditPort queryAudit(AuditRepositoryPort repository) {
        return new QueryAuditUseCase(repository);
    }

    @Bean
    @ConditionalOnProperty(name = "migrador.auditoria.anclas.programadas", havingValue = "true")
    DailyAuditAnchorJob dailyAuditAnchorJob(AnchorAuditPort anchorAudit) {
        return new DailyAuditAnchorJob(anchorAudit);
    }
}
