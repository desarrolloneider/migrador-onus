package com.guajiranet.migradoronu.consola;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/** Lo mismo que el proceso web, sin controladores ni seguridad HTTP. */
@SpringBootApplication
@ComponentScan(basePackages = {
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
        pattern = ".*\\.infraestructura\\.entrada\\.(web|seguridad|programacion)\\..*"))
public class ConsolaDeUsuariosApplication {
}
