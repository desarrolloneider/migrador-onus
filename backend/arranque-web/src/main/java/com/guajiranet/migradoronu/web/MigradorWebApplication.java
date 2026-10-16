package com.guajiranet.migradoronu.web;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.guajiranet.migradoronu.acceso.infraestructura.entrada.consola.UsuariosConsola;
import com.guajiranet.migradoronu.acceso.puertos.entrada.ManageUsersPort;
import com.guajiranet.migradoronu.comun.puertos.AuditTrailPort;
import com.guajiranet.migradoronu.consola.ConsolaDeUsuariosApplication;
import com.guajiranet.migradoronu.configuracion.dominio.OltSettings;
import com.guajiranet.migradoronu.configuracion.puertos.entrada.QueryParametersPort;
import com.guajiranet.migradoronu.ejecucion.aplicacion.CambioDePerfilUnaOnu;
import com.guajiranet.migradoronu.ejecucion.puertos.salida.OltConsolePort;
import com.guajiranet.migradoronu.integraciones.Diagnostico;
import com.guajiranet.migradoronu.integraciones.consola.SesionConsolaOlt;
import com.guajiranet.migradoronu.integraciones.consola.PruebaDeConsola;

/**
 * Proceso web: pantallas, API y tiempo real (arquitectura 9.1). Solo arma las piezas; no tiene lógica.
 *
 * <p>El escaneo es una lista explícita a propósito. Cada módulo se agrega cuando tenga sus casos de uso y sus
 * adaptadores, junto con el subpaquete de {@code ensamblaje} cuyos adaptadores ya tienen los dos módulos que unen
 * (docs/adr/0002).
 *
 * <p>{@code java -jar migrador-web.jar usuarios ...} no levanta el servidor: abre la consola de usuarios (ESP-02).
 * {@code diagnostico} prueba la conexión con SmartOLT y Wispro y lista los campos que traen.
 * {@code consola-prueba <OLT> [<F/S/P> <ONT>]} entra por SSH a esa OLT y ejecuta solo comandos display.
 * {@code cambio-prueba <OLT> <F/S/P> <ONT> [--real]} cambia el perfil de una sola ONU (sin --real, simula).
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
        "com.guajiranet.migradoronu.ensamblaje.lotes",
        "com.guajiranet.migradoronu.ensamblaje.consola"
})
@EnableScheduling
public class MigradorWebApplication {

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("usuarios")) {
            System.exit(consolaDeUsuarios(Arrays.copyOfRange(args, 1, args.length)));
        }
        if (args.length > 0 && args[0].equals("diagnostico")) {
            System.exit(diagnostico());
        }
        if (args.length > 3 && args[0].equals("cambio-prueba")) {
            System.exit(cambioDePrueba(args));
        }
        if (args.length > 1 && args[0].equals("consola-prueba")) {
            System.exit(pruebaDeConsola(args[1], args.length > 3 ? args[2] : null, args.length > 3 ? Integer.valueOf(args[3]) : null));
        }
        if (args.length > 1 && args[0].equals("perfiles-crudo")) {
            System.exit(perfilesCrudo(args[1]));
        }
        if (args.length > 0 && args[0].equals("archivar-lotes")) {
            System.exit(archivarLotes());
        }
        new SpringApplicationBuilder(MigradorWebApplication.class).run(args);
    }

    /** Archiva los lotes que ya no se mueven (no borra nada); queda en la auditoría como hecho desde la consola. */
    private static int archivarLotes() {
        try (ConfigurableApplicationContext contexto = contextoDeConsola()) {
            int n = contexto.getBean(com.guajiranet.migradoronu.lotes.puertos.entrada.ControlBatchPort.class).archivarTerminados(null, null);
            System.out.println(n + " lote(s) archivados. No se borró nada: siguen en la auditoría y el historial.");
            return 0;
        }
    }

    /** Solo lectura: imprime tal cual la tabla de perfiles TR-069 y el bound-info de cada uno (solo puertos y números). */
    private static int perfilesCrudo(String codigo) {
        try (ConfigurableApplicationContext contexto = contextoDeConsola();
             var consola = contexto.getBean(com.guajiranet.migradoronu.ejecucion.puertos.salida.OltConsoleFactoryPort.class).abrir(codigo)) {
            consola.ejecutar("enable");
            String tabla = consola.ejecutar("display ont tr069-server-profile all");
            System.out.println("=== display ont tr069-server-profile all\n" + visible(tabla));
            var t = com.guajiranet.migradoronu.procedimientos.dominio.TablaDePerfilesHuawei.leer(tabla);
            if (t.isEmpty()) {
                return 1;
            }
            for (var e : t.get().idPorNombre().entrySet()) {
                String texto = consola.ejecutar("display ont tr069-server-profile bound-info profile-id " + e.getValue());
                System.out.println("=== bound-info " + e.getKey() + " (" + e.getValue() + ") leible="
                        + com.guajiranet.migradoronu.procedimientos.dominio.ListaDePerfilHuawei.leer(texto).isPresent());
                System.out.println(visible(texto));
            }
            return 0;
        }
    }

    private static String visible(String texto) {
        return texto.replace("\b", "<BS>").replace("\r", "<CR>").replace("\u001b", "<ESC>");
    }

    private static int diagnostico() {
        try (ConfigurableApplicationContext contexto = contextoDeConsola()) {
            return new Diagnostico(System.out, contexto.getEnvironment()::getProperty).ejecutar();
        }
    }

    /** Solo lectura: entra por SSH a la OLT registrada y ejecuta los display del procedimiento. */
    private static int pruebaDeConsola(String codigo, String puertoOnu, Integer ontId) {
        try (ConfigurableApplicationContext contexto = contextoDeConsola()) {
            OltSettings olt = contexto.getBean(QueryParametersPort.class).oltSettings(codigo).orElse(null);
            if (olt == null || olt.direccion() == null || olt.puerto() == null) {
                System.out.println("La OLT " + codigo + " no está registrada o no tiene dirección y puerto.");
                return 2;
            }
            String prefijo = "CONSOLA_" + codigo.toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9]", "_");
            var entorno = contexto.getEnvironment();
            return new PruebaDeConsola(System.out).ejecutar(codigo, olt.direccion(), olt.puerto(),
                    com.guajiranet.migradoronu.ensamblaje.consola.ConsolasSshPorOlt.credencial(entorno, prefijo, "_USUARIO"), com.guajiranet.migradoronu.ensamblaje.consola.ConsolasSshPorOlt.credencial(entorno, prefijo, "_CLAVE"),
                    entorno.getProperty(prefijo + "_HUELLA"), puertoOnu, ontId);
        }
    }

    /**
     * Cambio de perfil de UNA ONU para la prueba piloto:
     * {@code cambio-prueba <OLT> <F/S/P> <ONT> [--real] [--permitir-fuera-de-linea]}. Sin --real solo simula.
     */
    private static int cambioDePrueba(String[] args) {
        List<String> opciones = Arrays.asList(args);
        boolean real = opciones.contains("--real");
        String codigo = args[1];
        String[] fsp = args[2].split("/");
        int ont = Integer.parseInt(args[3]);
        try (ConfigurableApplicationContext contexto = contextoDeConsola()) {
            OltSettings olt = contexto.getBean(QueryParametersPort.class).oltSettings(codigo).orElse(null);
            if (olt == null || olt.direccion() == null || olt.puerto() == null) {
                System.out.println("La OLT " + codigo + " no está registrada o no tiene dirección y puerto.");
                return 2;
            }
            var entorno = contexto.getEnvironment();
            String prefijo = "CONSOLA_" + codigo.toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9]", "_");
            var objetivo = new CambioDePerfilUnaOnu.Objetivo(codigo, Integer.parseInt(fsp[0]), Integer.parseInt(fsp[1]),
                    Integer.parseInt(fsp[2]), ont, List.of("GUAJIRANET"), "GUAJIRANET1", true, opciones.contains("--permitir-fuera-de-linea"), null);
            System.out.println((real ? "== CAMBIO REAL" : "== SIMULACIÓN") + " en " + codigo + " " + args[2] + " ONT " + ont
                    + ": perfil 2 -> 3 y guardar");
            var resultado = new CambioDePerfilUnaOnu(contexto.getBean(AuditTrailPort.class)).ejecutar(objetivo, real,
                    () -> new ConsolaSsh(SesionConsolaOlt.abrir(olt.direccion(), olt.puerto(), com.guajiranet.migradoronu.ensamblaje.consola.ConsolasSshPorOlt.credencial(entorno, prefijo, "_USUARIO"),
                            com.guajiranet.migradoronu.ensamblaje.consola.ConsolasSshPorOlt.credencial(entorno, prefijo, "_CLAVE"), entorno.getProperty(prefijo + "_HUELLA"),
                            Duration.ofSeconds(10), Duration.ofSeconds(90))));
            for (var paso : resultado.pasos()) {
                System.out.println();
                System.out.println("[" + paso.codigo() + "] " + (paso.bien() ? "OK   " : "FALLO") + "  " + paso.comando());
                System.out.println("       " + paso.mensaje());
                if (!paso.respuesta().isBlank() && !paso.comando().startsWith("display")) {
                    System.out.println(paso.respuesta().indent(7).stripTrailing());
                }
            }
            System.out.println();
            System.out.println(resultado.mensaje());
            return resultado.completado() || !real ? 0 : 1;
        }
    }

    /** La sesión SSH vista como el puerto de consola de ejecucion. */
    private record ConsolaSsh(SesionConsolaOlt sesion) implements OltConsolePort {
        @Override
        public String ejecutar(String comando) {
            return sesion.ejecutar(comando);
        }

        @Override
        public String prompt() {
            return sesion.prompt();
        }

        @Override
        public boolean esperarTexto(java.util.regex.Pattern patron, Duration maximo) {
            return sesion.esperarTexto(patron, maximo);
        }

        @Override
        public void close() {
            sesion.close();
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
