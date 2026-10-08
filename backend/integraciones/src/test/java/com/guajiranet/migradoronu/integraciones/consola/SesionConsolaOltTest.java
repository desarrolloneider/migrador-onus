package com.guajiranet.migradoronu.integraciones.consola;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SesionConsolaOltTest {

    private static final String COMANDO = "ont tr069-server-config 0 22 profile-id 3";

    @Test
    void elRetrocesoBorraYElRetornoVuelveAlInicioDeLaLinea() {
        assertThat(SesionConsolaOlt.limpiar("abc\b\bXY")).isEqualTo("aXY");
        assertThat(SesionConsolaOlt.limpiar("basura\rMA5608T#display")).isEqualTo("MA5608T#display");
        assertThat(SesionConsolaOlt.limpiar("linea uno\r\nlinea dos")).isEqualTo("linea uno\nlinea dos");
        assertThat(SesionConsolaOlt.limpiar("abcdef\u001B[3DXY")).isEqualTo("abcXY");
        assertThat(SesionConsolaOlt.limpiar("uno\n\b\bdos")).isEqualTo("uno\ndos");
    }

    /** Como pasó en OLT-CONEJO el 7 de octubre: un aviso interrumpe el eco y la OLT lo redibuja. */
    @Test
    void unEcoInterrumpidoPorUnAvisoQuedaIgualAlComando() {
        String recibido = "MA5608T(config-if-gpon-0/0)#ont tr069-se"
                + "\r\n  1 [2026-10-07 15:58:04-05:00]:The data of 2 slot's control board is saved completely\r\n"
                + "MA5608T(config-if-gpon-0/0)#ont tr069-se" + "\b".repeat(15) + COMANDO + "\r\n"
                + "MA5608T(config-if-gpon-0/0)#";
        String limpio = SesionConsolaOlt.limpiar(recibido);
        assertThat(limpio.lines()).anyMatch(l -> l.strip().endsWith(COMANDO));
        assertThat(limpio).doesNotContain("sver");
    }

    @Test
    void unAvisoDespuesDelPromptNoEsconderElFinDelComando() {
        String texto = "TR069 server profile ID      : 3\nMA5608T#\n"
                + "  1 [2026-10-07 16:18:53-05:00]:The percentage of saved data on 2 slot's control board is: 84%\n";
        assertThat(SesionConsolaOlt.ultimaLineaUtil(texto).strip()).isEqualTo("MA5608T#");
    }
}
