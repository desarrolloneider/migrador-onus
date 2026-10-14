package com.guajiranet.migradoronu.ejecucion.aplicacion;

import java.time.Duration;
import java.util.Objects;
import java.util.regex.Pattern;

import com.guajiranet.migradoronu.ejecucion.puertos.salida.OltConsolePort;
import com.guajiranet.migradoronu.procedimientos.dominio.RespuestaHuawei;
import com.guajiranet.migradoronu.procedimientos.dominio.TablaDePerfilesHuawei;

/**
 * La consola que comparten las ONUs de una OLT en un mismo hilo del ejecutor. Ahorra los comandos que se repiten igual
 * en cada ONU, sin quitar ninguna lectura de la ONU (foto previa y verificación siguen siendo una por una):
 * <ul>
 *   <li>{@code enable}: no se manda si ya está en modo privilegiado (prompt con #).</li>
 *   <li>La tabla de perfiles TR-069: se lee una vez por sesión (no cambia mientras corre el lote).</li>
 *   <li>{@code config}: no se manda si ya está en modo configuración; el último {@code quit} de cada ONU no sale de él.</li>
 *   <li>Nunca sale de la sesión con {@code quit}: la cierra el ejecutor al terminar con la OLT.</li>
 * </ul>
 * Así cada ONU pasa de 9 comandos a 5. Del {@code quit} de la tarjeta sí se manda: sale a modo configuración.
 */
final class ConsolaCompartida implements OltConsolePort {

    static final String TABLA = "display ont tr069-server-profile all";

    private final OltConsolePort consola;
    private String tablaGuardada;

    ConsolaCompartida(OltConsolePort consola) {
        this.consola = Objects.requireNonNull(consola, "consola");
    }

    @Override
    public String ejecutar(String comando) {
        String c = comando.strip();
        String prompt = consola.prompt();
        boolean enTarjeta = prompt.contains("-if-");
        boolean enConfiguracion = prompt.contains("(config)");
        if (c.equals("enable") && prompt.endsWith("#")) {
            return "";
        }
        if (c.equals("config") && enConfiguracion) {
            return "";
        }
        if (c.equals("quit") && !enTarjeta) {
            // Ni salir de configuración (la siguiente ONU vuelve a entrar) ni cerrar la sesión.
            return "";
        }
        if (c.equals(TABLA)) {
            if (tablaGuardada != null) {
                return tablaGuardada;
            }
            String respuesta = consola.ejecutar(comando);
            if (!RespuestaHuawei.esError(respuesta) && TablaDePerfilesHuawei.leer(respuesta).isPresent()) {
                tablaGuardada = respuesta;
            }
            return respuesta;
        }
        return consola.ejecutar(comando);
    }

    @Override
    public String prompt() {
        return consola.prompt();
    }

    @Override
    public boolean esperarTexto(Pattern patron, Duration maximo) {
        return consola.esperarTexto(patron, maximo);
    }

    @Override
    public void close() {
        // La cierra el ejecutor al terminar con la OLT.
    }
}
