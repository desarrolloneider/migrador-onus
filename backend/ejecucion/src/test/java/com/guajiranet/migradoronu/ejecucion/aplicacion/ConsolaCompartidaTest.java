package com.guajiranet.migradoronu.ejecucion.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.guajiranet.migradoronu.ejecucion.puertos.salida.OltConsolePort;

class ConsolaCompartidaTest {

    private static final String TABLA_OK = """
            --------------------------------------------------------------
             Profile ID     Profile Name                     Binding Times
             --------------------------------------------------------------
             1              SmartOLT                         0
             3              GUAJIRANET1                      217
             --------------------------------------------------------------
            """;

    /** Una OLT de mentira que lleva el modo (prompt) como la Huawei y anota lo que de verdad le llega. */
    private static class OltFalsa implements OltConsolePort {
        final List<String> recibidos = new ArrayList<>();
        String prompt = "MA5608T>";

        @Override
        public String ejecutar(String comando) {
            recibidos.add(comando);
            switch (comando) {
                case "enable" -> prompt = "MA5608T#";
                case "config" -> prompt = "MA5608T(config)#";
                case "quit" -> prompt = prompt.contains("-if-") ? "MA5608T(config)#" : prompt.contains("(config)") ? "MA5608T#" : "cerrada";
                default -> {
                    if (comando.startsWith("interface gpon")) {
                        prompt = "MA5608T(config-if-gpon-0/5)#";
                    }
                }
            }
            return comando.equals(ConsolaCompartida.TABLA) ? TABLA_OK : "";
        }

        @Override
        public String prompt() {
            return prompt;
        }

        @Override
        public boolean esperarTexto(Pattern patron, Duration maximo) {
            return true;
        }

        @Override
        public void close() {
        }
    }

    /** Los comandos que manda CambioDePerfilUnaOnu para una ONU real. */
    private static void unaOnu(OltConsolePort c, int ont) {
        c.ejecutar("enable");
        c.ejecutar(ConsolaCompartida.TABLA);
        c.ejecutar("display ont info 0 5 11 " + ont);
        c.ejecutar("config");
        assertThat(c.prompt()).contains("(config)");
        c.ejecutar("interface gpon 0/5");
        c.ejecutar("ont tr069-server-config 11 " + ont + " profile-id 3");
        c.ejecutar("quit");
        c.ejecutar("display ont info 0 5 11 " + ont);
        c.ejecutar("quit");
    }

    @Test
    void laSegundaOnuSoloMandaLoQueEsDeEllaYLaSesionNoSeCierra() {
        OltFalsa olt = new OltFalsa();
        ConsolaCompartida c = new ConsolaCompartida(olt);
        unaOnu(c, 55);
        int primera = olt.recibidos.size();
        unaOnu(c, 56);
        List<String> segunda = olt.recibidos.subList(primera, olt.recibidos.size());
        assertThat(segunda).containsExactly("display ont info 0 5 11 56", "interface gpon 0/5",
                "ont tr069-server-config 11 56 profile-id 3", "quit", "display ont info 0 5 11 56");
        assertThat(olt.prompt).isEqualTo("MA5608T(config)#");
    }

    @Test
    void laPrimeraOnuEntraComoSiempre() {
        OltFalsa olt = new OltFalsa();
        unaOnu(new ConsolaCompartida(olt), 55);
        assertThat(olt.recibidos).startsWith("enable", ConsolaCompartida.TABLA).contains("config");
    }

    @Test
    void unaTablaQueNoSeLeyoBienSeVuelveAPedir() {
        OltFalsa olt = new OltFalsa() {
            @Override
            public String ejecutar(String comando) {
                recibidos.add(comando);
                return comando.equals(ConsolaCompartida.TABLA) ? "Failure: System is busy" : "";
            }
        };
        ConsolaCompartida c = new ConsolaCompartida(olt);
        c.ejecutar(ConsolaCompartida.TABLA);
        c.ejecutar(ConsolaCompartida.TABLA);
        assertThat(olt.recibidos).containsExactly(ConsolaCompartida.TABLA, ConsolaCompartida.TABLA);
    }
}
