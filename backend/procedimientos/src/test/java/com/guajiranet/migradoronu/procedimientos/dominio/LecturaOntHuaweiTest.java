package com.guajiranet.migradoronu.procedimientos.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LecturaOntHuaweiTest {

    /** Extracto real de OLT-CONEJO 0/0/6 ONT 2 (sin la descripción). */
    private static final String SALIDA = """
              F/S/P                   : 0/0/6
              ONT-ID                  : 2
              Run state               : offline
              SN                      : 48575443FCAA95B0 (HWTC-FCAA95B0)
              TR069 management    :Enable
              -----------------------------------------------------------------------------
              TR069 server profile ID      : 2
              TR069 server profile name    : GUAJIRANET
              -----------------------------------------------------------------------------
            """;

    @Test
    void leeSnEstadoYPerfil() {
        LecturaOntHuawei l = LecturaOntHuawei.leer(SALIDA).orElseThrow();
        assertThat(l.sn()).isEqualTo("48575443FCAA95B0");
        assertThat(l.enLinea()).isFalse();
        assertThat(l.perfilId()).contains(2);
        assertThat(l.perfilNombre()).contains("GUAJIRANET");
    }

    @Test
    void enLineaYPerfilNuevo() {
        LecturaOntHuawei l = LecturaOntHuawei.leer(SALIDA.replace("offline", "online").replace("ID      : 2", "ID      : 3")
                .replace("GUAJIRANET\n", "GUAJIRANET1\n")).orElseThrow();
        assertThat(l.enLinea()).isTrue();
        assertThat(l.perfilId()).contains(3);
        assertThat(l.perfilNombre()).contains("GUAJIRANET1");
    }

    @Test
    void unErrorNoSeInterpreta() {
        assertThat(LecturaOntHuawei.leer("  % Failure: The ONT does not exist")).isEmpty();
    }
}
