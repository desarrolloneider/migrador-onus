package com.guajiranet.migradoronu.procedimientos.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RespuestaHuaweiTest {

    /** Respuesta real de OLT-CONEJO del 7 de octubre, con el aviso del guardado intercalado. */
    private static final String TABLA_CON_AVISO = """
            display ont tr069-server-profile
              1 [2026-10-07 14:57:47-05:00]:The percentage of saved data on 2 slot's control board is: 78%\s

            MA5608T#display ont tr069-server-profile all
             --------------------------------------------------------------
             Profile ID     Profile Name                     Binding Times
             --------------------------------------------------------------
             2              GUAJIRANET                       5
             3              GUAJIRANET1                      13
             --------------------------------------------------------------
             Total: 2
            """;

    @Test
    void unPorcentajeNoEsUnError() {
        assertThat(RespuestaHuawei.esError(TABLA_CON_AVISO)).isFalse();
        assertThat(TablaDePerfilesHuawei.leer(TABLA_CON_AVISO).orElseThrow().idDe("GUAJIRANET1")).contains(3);
        assertThat(RespuestaHuawei.esError("  Memory occupation       : 48%\n  CPU occupation          : 1%")).isFalse();
    }

    @Test
    void erroresDeVerdad() {
        assertThat(RespuestaHuawei.esError("                    ^\n  % Unknown command, the error locates at '^'")).isTrue();
        assertThat(RespuestaHuawei.esError("  Failure: System is busy, please retry after a while")).isTrue();
        assertThat(RespuestaHuawei.estaOcupada("  Failure: System is busy, please retry after a while")).isTrue();
        assertThat(RespuestaHuawei.estaOcupada("  % Parameter error")).isFalse();
    }

    @Test
    void quitaLosAvisosAsincronos() {
        assertThat(RespuestaHuawei.sinAvisos("a\n  1 [2026-10-07 14:56:57-05:00]:The percentage of saved data is: 66%\nb")).isEqualTo("a\nb");
    }
}
