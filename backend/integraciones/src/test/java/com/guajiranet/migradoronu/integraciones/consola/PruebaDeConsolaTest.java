package com.guajiranet.migradoronu.integraciones.consola;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PruebaDeConsolaTest {

    /** Misma forma que la salida real de display ont info en OLT-CONEJO, con un nombre inventado. */
    private static final String SALIDA = """
              SN                      : 4857544347ADC1B3 (HWTC-47ADC1B3)
              ONT IP 0 address/mask   : -
              Description             : 993 10.7.0.35 (Cliente
                                        Inventado De Prueba)_P3
                                                   Last down cause         : dying-gasp
              Last up time            : 2026-10-06 20:08:43-05:00
              TR069 server profile ID      : 2
            """;

    @Test
    void ocultaLaDescripcionCompletaAunqueOcupeVariasLineas() {
        String limpio = PruebaDeConsola.sinDatosDeCliente(SALIDA);
        assertThat(limpio).doesNotContain("Inventado", "Cliente", "10.7.0.35", "_P3");
        assertThat(limpio).contains("Description             : (oculta)", "Last down cause         : dying-gasp",
                "Last up time", "TR069 server profile ID      : 2", "SN                      : 4857544347ADC1B3");
    }

    @Test
    void sinDescripcionNoCambiaNada() {
        assertThat(PruebaDeConsola.sinDatosDeCliente("  F/S/P : 0/0/4\n  ONT-ID : 16")).isEqualTo("  F/S/P : 0/0/4\n  ONT-ID : 16");
    }
}
