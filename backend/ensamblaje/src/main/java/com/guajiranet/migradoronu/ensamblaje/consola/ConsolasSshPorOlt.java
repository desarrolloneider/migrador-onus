package com.guajiranet.migradoronu.ensamblaje.consola;

import java.time.Duration;
import java.util.Locale;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.guajiranet.migradoronu.configuracion.dominio.OltSettings;
import com.guajiranet.migradoronu.configuracion.puertos.entrada.QueryParametersPort;
import com.guajiranet.migradoronu.ejecucion.puertos.salida.OltConsoleFactoryPort;
import com.guajiranet.migradoronu.ejecucion.puertos.salida.OltConsolePort;
import com.guajiranet.migradoronu.integraciones.consola.SesionConsolaOlt;

/**
 * Abre la consola SSH de una OLT registrada. Dirección y puerto salen de la tabla olts; usuario, contraseña y huella de
 * la llave, del .env ({@code CONSOLA_<CODIGO>_USUARIO}, {@code _CLAVE}, {@code _HUELLA}): nunca de la base (RN-25).
 * Si la OLT no tiene usuario o clave propios, usa {@code CONSOLA_GENERAL_USUARIO} y {@code _CLAVE}. La huella es siempre
 * por OLT. Sin huella registrada no se conecta.
 */
@Component
public class ConsolasSshPorOlt implements OltConsoleFactoryPort {

    private final QueryParametersPort configuracion;
    private final Environment entorno;

    public ConsolasSshPorOlt(QueryParametersPort configuracion, Environment entorno) {
        this.configuracion = configuracion;
        this.entorno = entorno;
    }

    @Override
    public OltConsolePort abrir(String oltCodigo) {
        OltSettings olt = configuracion.oltSettings(oltCodigo)
                .filter(OltSettings::consoleAccessConfigured)
                .orElseThrow(() -> new IllegalStateException(oltCodigo + " no tiene configurado el acceso por consola."));
        String prefijo = "CONSOLA_" + oltCodigo.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "_");
        String huella = huella(entorno, configuracion, oltCodigo);
        if (huella == null || huella.isBlank()) {
            throw new IllegalStateException("No está registrada la huella SSH de " + oltCodigo + " (" + prefijo + "_HUELLA): no se conecta.");
        }
        SesionConsolaOlt sesion = SesionConsolaOlt.abrir(olt.direccion(), olt.puerto(), credencial(entorno, prefijo, "_USUARIO"),
                credencial(entorno, prefijo, "_CLAVE"), huella, Duration.ofSeconds(10), Duration.ofSeconds(90));
        return new OltConsolePort() {
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
        };
    }

    /** La huella del .env ({@code CONSOLA_<CODIGO>_HUELLA}) o, si no está, la fijada desde Configuración. */
    public static String huella(Environment entorno, QueryParametersPort configuracion, String oltCodigo) {
        String delEnv = entorno.getProperty(prefijo(oltCodigo) + "_HUELLA");
        return delEnv != null && !delEnv.isBlank() ? delEnv : configuracion.huellaSsh(oltCodigo).orElse(null);
    }

    public static String prefijo(String oltCodigo) {
        return "CONSOLA_" + oltCodigo.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "_");
    }

    /** El valor propio de la OLT o, si no tiene, el general ({@code CONSOLA_GENERAL...}). */
    public static String credencial(Environment entorno, String prefijo, String sufijo) {
        String propio = entorno.getProperty(prefijo + sufijo);
        return propio != null && !propio.isBlank() ? propio : entorno.getProperty("CONSOLA_GENERAL" + sufijo);
    }
}
