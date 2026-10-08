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
 * Sin huella registrada no se conecta.
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
        String huella = entorno.getProperty(prefijo + "_HUELLA");
        if (huella == null || huella.isBlank()) {
            throw new IllegalStateException("No está registrada la huella SSH de " + oltCodigo + " (" + prefijo + "_HUELLA): no se conecta.");
        }
        SesionConsolaOlt sesion = SesionConsolaOlt.abrir(olt.direccion(), olt.puerto(), entorno.getProperty(prefijo + "_USUARIO"),
                entorno.getProperty(prefijo + "_CLAVE"), huella, Duration.ofSeconds(10), Duration.ofSeconds(90));
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
}
