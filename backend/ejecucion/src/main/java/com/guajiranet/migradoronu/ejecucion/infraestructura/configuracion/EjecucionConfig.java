package com.guajiranet.migradoronu.ejecucion.infraestructura.configuracion;

import java.time.Clock;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.guajiranet.migradoronu.comun.puertos.AuditTrailPort;
import com.guajiranet.migradoronu.comun.puertos.RunTimelinePort;
import com.guajiranet.migradoronu.ejecucion.aplicacion.CambioDePerfilUnaOnu;
import com.guajiranet.migradoronu.ejecucion.aplicacion.ConsultarEjecucionesUseCase;
import com.guajiranet.migradoronu.ejecucion.aplicacion.LeerPerfilesDeOlt;
import com.guajiranet.migradoronu.ejecucion.infraestructura.salida.persistencia.JdbcColaDeEjecucionAdapter;
import com.guajiranet.migradoronu.ejecucion.infraestructura.salida.persistencia.JdbcHistorialDeOnusAdapter;
import com.guajiranet.migradoronu.ejecucion.infraestructura.salida.persistencia.JdbcLecturasDeEjecucionAdapter;
import com.guajiranet.migradoronu.ejecucion.infraestructura.salida.persistencia.JdbcQueryOnuRunAdapter;
import com.guajiranet.migradoronu.ejecucion.infraestructura.salida.persistencia.JdbcScheduleOnuRunsAdapter;
import com.guajiranet.migradoronu.ejecucion.puertos.entrada.QueryOnuRunPort;
import com.guajiranet.migradoronu.ejecucion.puertos.entrada.ReadOltProfilesPort;
import com.guajiranet.migradoronu.ejecucion.puertos.salida.OltConsoleFactoryPort;
import com.guajiranet.migradoronu.ejecucion.puertos.entrada.ScheduleOnuRunsPort;
import com.guajiranet.migradoronu.ejecucion.puertos.salida.ColaDeEjecucionPort;
import com.guajiranet.migradoronu.ejecucion.puertos.salida.HistorialDeOnusPort;

/** Arma el módulo ejecucion. El ejecutor (EjecutorDeOnus) lo arma el proceso ejecutor, que es quien abre consolas. */
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

    @Bean
    ColaDeEjecucionPort colaDeEjecucion(DataSource dataSource, PlatformTransactionManager transacciones) {
        return new JdbcColaDeEjecucionAdapter(dataSource, transacciones);
    }

    @Bean
    CambioDePerfilUnaOnu cambioDePerfilUnaOnu(AuditTrailPort auditoria) {
        return new CambioDePerfilUnaOnu(auditoria);
    }

    @Bean
    ConsultarEjecucionesUseCase consultarEjecuciones(DataSource dataSource, RunTimelinePort lineaDeTiempo, AuditTrailPort auditoria,
                                                     Clock reloj) {
        return new ConsultarEjecucionesUseCase(new JdbcLecturasDeEjecucionAdapter(dataSource), lineaDeTiempo, auditoria, reloj);
    }

    @Bean
    ReadOltProfilesPort readOltProfiles(OltConsoleFactoryPort consolas, Clock reloj) {
        return new LeerPerfilesDeOlt(consolas, reloj);
    }

    @Bean
    HistorialDeOnusPort historialDeOnus(DataSource dataSource) {
        return new JdbcHistorialDeOnusAdapter(dataSource);
    }
}
