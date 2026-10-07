package com.guajiranet.migradoronu.lotes.infraestructura.configuracion;

import java.time.Clock;
import java.util.function.Supplier;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.guajiranet.migradoronu.comun.puertos.AuditTrailPort;
import com.guajiranet.migradoronu.lotes.aplicacion.LotesUseCase;
import com.guajiranet.migradoronu.lotes.infraestructura.salida.persistencia.JdbcBatchRepositoryAdapter;
import com.guajiranet.migradoronu.lotes.infraestructura.salida.persistencia.JdbcQueryBatchAdapter;
import com.guajiranet.migradoronu.lotes.puertos.entrada.QueryBatchPort;
import com.guajiranet.migradoronu.lotes.puertos.salida.OnuClassifierPort;
import com.guajiranet.migradoronu.lotes.puertos.salida.ParametersSnapshotPort;
import com.guajiranet.migradoronu.lotes.puertos.salida.RunSchedulerPort;
import com.guajiranet.migradoronu.lotes.puertos.salida.TransactionPort;

/** Arma el módulo lotes. Un solo caso de uso implementa armar, controlar y consultar el detalle. */
@Configuration(proxyBeanMethods = false)
public class LotesConfig {

    @Bean
    QueryBatchPort queryBatch(DataSource dataSource) {
        return new JdbcQueryBatchAdapter(dataSource);
    }

    @Bean
    LotesUseCase lotesUseCase(DataSource dataSource, OnuClassifierPort clasificador, ParametersSnapshotPort parametros,
                              RunSchedulerPort ejecuciones, AuditTrailPort auditoria, PlatformTransactionManager transacciones,
                              Clock reloj, @Value("${migrador.procedimiento.version:huawei-tr069-borrador}") String version) {
        TransactionTemplate plantilla = new TransactionTemplate(transacciones);
        TransactionPort transaccion = new TransactionPort() {
            @Override
            public <T> T enTransaccion(Supplier<T> trabajo) {
                return plantilla.execute(estado -> trabajo.get());
            }
        };
        return new LotesUseCase(clasificador, parametros, ejecuciones, new JdbcBatchRepositoryAdapter(dataSource), transaccion,
                auditoria, () -> version, reloj);
    }
}
