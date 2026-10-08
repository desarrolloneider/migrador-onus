package com.guajiranet.migradoronu.procedimientos.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ListaDePerfilHuaweiTest {

    /** Salida real de OLT-CONEJO, perfil 3, del 7 de octubre de 2026. */
    private static final String PERFIL_3 = """
            ------------------------------------------------------------------------------
              F/S/P       ONT List
              ------------------------------------------------------------------------------
              0/ 0/0      12,30
              0/ 0/2      9
              0/ 0/5      53
              0/ 0/7      2,12-13
              0/ 1/1      7
              0/ 1/6      12
              ------------------------------------------------------------------------------
            """;

    @Test
    void leeLaSalidaRealConRangos() {
        ListaDePerfilHuawei lista = ListaDePerfilHuawei.leer(PERFIL_3).orElseThrow();
        assertThat(lista.total()).isEqualTo(9);
        assertThat(lista.ontsPorPuerto().get("0/0/7")).containsExactly(2, 12, 13);
        assertThat(lista.contiene("0/1", 6, 12)).isTrue();
        assertThat(lista.contiene("0/0", 7, 14)).isFalse();
        assertThat(lista.contiene("0/0", 3, 1)).isFalse();
    }

    @Test
    void unErrorDeLaOltNoSeInterpreta() {
        assertThat(ListaDePerfilHuawei.leer("  % Unknown command, the error locates at '^'")).isEmpty();
    }

    @Test
    void unaLineaDesconocidaNoSeAdivina() {
        assertThat(ListaDePerfilHuawei.leer(PERFIL_3 + "  algo raro aquí\n")).isEmpty();
    }

    @Test
    void sinOntsEsUnaListaVacia() {
        assertThat(ListaDePerfilHuawei.leer("""
                ------------------------------------------------------------------------------
                  F/S/P       ONT List
                  ------------------------------------------------------------------------------
                """).orElseThrow().total()).isZero();
    }
}
