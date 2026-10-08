package com.guajiranet.migradoronu.procedimientos.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TablaDePerfilesHuaweiTest {

    @Test
    void leeLaTablaReal() {
        TablaDePerfilesHuawei t = TablaDePerfilesHuawei.leer("""
                --------------------------------------------------------------
                 Profile ID     Profile Name                     Binding Times
                 --------------------------------------------------------------
                 2              GUAJIRANET                       6
                 3              GUAJIRANET1                      10
                 --------------------------------------------------------------
                 Total: 2
                """).orElseThrow();
        assertThat(t.idDe("GUAJIRANET")).contains(2);
        assertThat(t.idDe("guajiranet1")).contains(3);
        assertThat(t.idDe("SmartOLT")).isEmpty();
    }

    @Test
    void unErrorNoSeInterpreta() {
        assertThat(TablaDePerfilesHuawei.leer("  % Unknown command, the error locates at '^'")).isEmpty();
    }
}
