package com.guajiranet.migradoronu.procedimientos.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ValoresDelContratoTest {

    @Test
    void laTarjetaSeLeeEnFormatoMarcoRanura() {
        assertThat(BoardRef.parse(" 0/1 ")).isEqualTo(new BoardRef(0, 1));
        assertThat(new BoardRef(0, 12)).hasToString("0/12");
        assertThatThrownBy(() -> BoardRef.parse("1")).hasMessageContaining("formato esperado 0/n");
        assertThatThrownBy(() -> BoardRef.parse(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elPerfilNoAdmiteEspacios() {
        assertThat(new Tr069ProfileName("guajiranet1")).hasToString("guajiranet1");
        assertThatThrownBy(() -> new Tr069ProfileName("guajiranet 1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Tr069ProfileName(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unaLecturaSinPerfilNoTieneNingunPerfil() {
        Tr069ProfileName guajiranet1 = new Tr069ProfileName("guajiranet1");

        assertThat(Tr069Reading.withoutProfile("...").hasProfile(guajiranet1)).isFalse();
        assertThat(Tr069Reading.withProfile(guajiranet1, "...").hasProfile(guajiranet1)).isTrue();
        assertThat(Tr069Reading.withoutProfile("...").currentProfile()).isEmpty();
    }

    @Test
    void unComandoPlaneadoExigeLosDatosDeSuOperacion() {
        OnuPosition position = new OnuPosition(0, 33);

        assertThatThrownBy(() -> PlannedCommand.of(StepCode.P04, OltOperation.ENTER_BOARD)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PlannedCommand.onOnu(StepCode.P06, OltOperation.ASSIGN_TR069_PROFILE, position))
                .hasMessageContaining("perfil");
        assertThat(PlannedCommand.onOnu(StepCode.P05, OltOperation.REMOVE_TR069_PROFILE, position).operation().changesOnu()).isTrue();
        assertThat(PlannedCommand.none(StepCode.P01).isNone()).isTrue();
    }

    @Test
    void siAlgoCambioHayQueDecirQue() {
        assertThatThrownBy(() -> new RevalidationResult(false, null, "a", "b")).hasMessageContaining("qué dato");
        assertThat(RevalidationResult.changed("ip", "10.0.0.1", "10.0.0.2").unchanged()).isFalse();
    }
}
