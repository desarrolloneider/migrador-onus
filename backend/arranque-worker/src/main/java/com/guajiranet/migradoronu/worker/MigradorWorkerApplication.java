package com.guajiranet.migradoronu.worker;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.guajiranet.migradoronu.ejecucion.aplicacion.CambioDePerfilUnaOnu;
import com.guajiranet.migradoronu.ejecucion.aplicacion.EjecutorDeOnus;
import com.guajiranet.migradoronu.ejecucion.puertos.salida.ColaDeEjecucionPort;
import com.guajiranet.migradoronu.ejecucion.puertos.salida.OltConsoleFactoryPort;

/**
 * Proceso ejecutor: toma las ONUs confirmadas y les cambia el perfil por consola (arquitectura 9.1). Sin pantallas.
 * Arma los mismos módulos que la web, sin sus controladores ni su seguridad HTTP.
 */
@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {
        "com.guajiranet.migradoronu.worker",
        "com.guajiranet.migradoronu.comun",
        "com.guajiranet.migradoronu.auditoria",
        "com.guajiranet.migradoronu.acceso",
        "com.guajiranet.migradoronu.configuracion",
        "com.guajiranet.migradoronu.lotes",
        "com.guajiranet.migradoronu.conciliacion",
        "com.guajiranet.migradoronu.ejecucion",
        "com.guajiranet.migradoronu.ensamblaje.transversal",
        "com.guajiranet.migradoronu.ensamblaje.acceso",
        "com.guajiranet.migradoronu.ensamblaje.configuracion",
        "com.guajiranet.migradoronu.ensamblaje.conciliacion",
        "com.guajiranet.migradoronu.ensamblaje.lotes",
        "com.guajiranet.migradoronu.ensamblaje.consola"
}, excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
        pattern = ".*\\.infraestructura\\.entrada\\.(web|seguridad)\\..*"))
public class MigradorWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MigradorWorkerApplication.class, args);
    }

    @Bean
    EjecutorDeOnus ejecutorDeOnus(ColaDeEjecucionPort cola, OltConsoleFactoryPort consolas, CambioDePerfilUnaOnu cambio,
                                  @Value("${migrador.perfil-origen:GUAJIRANET}") String perfilOrigen,
                                  com.guajiranet.migradoronu.comun.puertos.AuditTrailPort auditoria,
                                  com.guajiranet.migradoronu.ejecucion.puertos.salida.GestionSmartOltPort smartOlt,
                                  @Value("${MIGRADOR_VLAN_GESTION:46}") int vlanGestion) {
        return new EjecutorDeOnus(cola, consolas, cambio, perfilOrigen, auditoria,
                new com.guajiranet.migradoronu.ejecucion.aplicacion.CambioDeGestionUnaOnu(smartOlt, auditoria), vlanGestion);
    }
}
