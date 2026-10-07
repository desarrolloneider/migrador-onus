package com.guajiranet.migradoronu.web;

import java.util.Arrays;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import com.guajiranet.migradoronu.acceso.infraestructura.entrada.consola.UsuariosConsola;
import com.guajiranet.migradoronu.acceso.puertos.entrada.ManageUsersPort;
import com.guajiranet.migradoronu.consola.ConsolaDeUsuariosApplication;
import com.guajiranet.migradoronu.integraciones.Diagnostico;

/**
 * Proceso web: pantallas, API y tiempo real (arquitectura 9.1). Solo arma las piezas; no tiene lógica.
 *
 * <p>El escaneo es una lista explícita a propósito. Cada módulo se agrega cuando tenga sus casos de uso y sus
 * adaptadores, junto con el subpaquete de {@code ensamblaje} cuyos adaptadores ya tienen los dos módulos que unen
 * (docs/adr/0002).
 *
 * <p>{@code java -jar migrador-web.jar usuarios ...} no levanta el servidor: abre la consola de usuarios (ESP-02).
 * {@code diagnostico} prueba la conexión con SmartOLT y Wispro y lista los campos que traen.
 */
@SpringBootApplication(scanBasePackages = {
        "com.guajiranet.migradoronu.web",
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
        "com.guajiranet.migradoronu.ensamblaje.lotes"
})
public class MigradorWebApplication {

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("usuarios")) {
            System.exit(consolaDeUsuarios(Arrays.copyOfRange(args, 1, args.length)));
        }
        if (args.length > 0 && args[0].equals("diagnostico")) {
            System.exit(diagnostico());
        }
        new SpringApplicationBuilder(MigradorWebApplication.class).run(args);
    }

    private static int diagnostico() {
        try (ConfigurableApplicationContext contexto = contextoDeConsola()) {
            return new Diagnostico(System.out, contexto.getEnvironment()::getProperty).ejecutar();
        }
    }

    private static ConfigurableApplicationContext contextoDeConsola() {
        return new SpringApplicationBuilder(ConsolaDeUsuariosApplication.class)
                .web(WebApplicationType.NONE)
                .logStartupInfo(false)
                .properties("spring.main.banner-mode=off", "logging.level.root=WARN", "spring.task.scheduling.enabled=false")
                .run();
    }

    private static int consolaDeUsuarios(String[] args) {
        try (ConfigurableApplicationContext contexto = contextoDeConsola()) {
            return new UsuariosConsola(contexto.getBean(ManageUsersPort.class), System.out, UsuariosConsola.entradaDelSistema())
                    .ejecutar(args);
        }
    }
}
