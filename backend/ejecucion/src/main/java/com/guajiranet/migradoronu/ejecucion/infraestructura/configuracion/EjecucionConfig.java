package com.guajiranet.migradoronu.ejecucion.infraestructura.configuracion;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.guajiranet.migradoronu.ejecucion.infraestructura.salida.persistencia.JdbcQueryOnuRunAdapter;
import com.guajiranet.migradoronu.ejecucion.infraestructura.salida.persistencia.JdbcScheduleOnuRunsAdapter;
import com.guajiranet.migradoronu.ejecucion.puertos.entrada.QueryOnuRunPort;
import com.guajiranet.migradoronu.ejecucion.puertos.entrada.ScheduleOnuRunsPort;

/** Arma el módulo ejecucion. Por ahora solo crear las ejecuciones al confirmar; el ejecutor llega con el acceso a las OLT. */
@Configuration(proxyBeanMethods = false)
public class EjecucionConfig {

    @Bean
    ScheduleOnuRunsPort scheduleOnuRuns(DataSource dataSource) {
        return new JdbcScheduleOnuRunsAdapter(dataSource);
    }

    @Bean
    QueryOnuRunPort queryOnuRuns(DataSource dataSource) {
        return new JdbcQueryOnuRunAdapter(dataSource);
    }
}
