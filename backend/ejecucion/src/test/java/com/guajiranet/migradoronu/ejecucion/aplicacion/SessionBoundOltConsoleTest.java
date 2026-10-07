package com.guajiranet.migradoronu.ejecucion.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.guajiranet.migradoronu.ejecucion.dominio.OltRef;
import com.guajiranet.migradoronu.ejecucion.dominio.OltSessionHandle;
import com.guajiranet.migradoronu.ejecucion.puertos.salida.OltTr069ConfigPort;
import com.guajiranet.migradoronu.procedimientos.dominio.BoardRef;
import com.guajiranet.migradoronu.procedimientos.dominio.CommandOutcome;
import com.guajiranet.migradoronu.procedimientos.dominio.OnuPosition;
import com.guajiranet.migradoronu.procedimientos.dominio.OnuTarget;
import com.guajiranet.migradoronu.procedimientos.dominio.PlannedCommand;
import com.guajiranet.migradoronu.procedimientos.dominio.RevalidationResult;
import com.guajiranet.migradoronu.procedimientos.dominio.StepCode;
import com.guajiranet.migradoronu.procedimientos.dominio.Tr069ProfileName;
import com.guajiranet.migradoronu.procedimientos.dominio.Tr069Reading;

class SessionBoundOltConsoleTest {

    private static final OltRef OLT = new OltRef("RIOHACHA01");
    private static final OnuPosition ONU = new OnuPosition(0, 33);
    private static final Tr069ProfileName GUAJIRANET1 = new Tr069ProfileName("guajiranet1");

    private final RecordingPort port = new RecordingPort();

    @Test
    void usaLaMismaSesionDesdeQueSeAbreHastaQueSeCierra() {
        SessionBoundOltConsole console = new SessionBoundOltConsole(port, OLT);

        console.openSession();
        console.readTr069Config(ONU);
        console.enterConfigMode();
        console.enterBoard(new BoardRef(0, 1));
        console.removeTr069Profile(ONU);
        console.assignTr069Profile(ONU, GUAJIRANET1);
        console.closeSession();

        assertThat(port.calls).containsExactly(
                "open RIOHACHA01",
                "read s-1 0/33",
                "config s-1",
                "board s-1 0/1",
                "undo s-1 0/33",
                "assign s-1 0/33 guajiranet1",
                "close s-1");
        assertThat(console.isOpen()).isFalse();
    }

    @Test
    void ningunaOperacionSobreLaOnuSaleSinSesionAbierta() {
        SessionBoundOltConsole console = new SessionBoundOltConsole(port, OLT);

        assertThatThrownBy(() -> console.removeTr069Profile(ONU))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("P02 debe ir antes");
        assertThat(port.calls).isEmpty();
    }

    @Test
    void noAbreDosSesionesParaLaMismaOnu() {
        SessionBoundOltConsole console = new SessionBoundOltConsole(port, OLT);
        console.openSession();

        assertThatThrownBy(console::openSession).hasMessageContaining("RN-34");
        assertThat(port.calls).containsExactly("open RIOHACHA01");
    }

    @Test
    void cerrarSinSesionNoMandaNada() {
        assertThat(new SessionBoundOltConsole(port, OLT).closeSession().kind()).isEqualTo(CommandOutcome.Kind.SIN_COMANDO);
        assertThat(port.calls).isEmpty();
    }

    @Test
    void elContextoArmaLasCapacidadesSobreLosPuertosDeEjecucion() {
        OnuTarget target = new OnuTarget("HWTC1A2B3C4D", "10.20.30.40", "C-1001", "RIOHACHA01", new BoardRef(0, 1), ONU);
        OnuStepContext context = OnuStepContext.forOnu(target, GUAJIRANET1, port, t -> RevalidationResult.same());

        context.olt().openSession();
        Tr069Reading reading = context.olt().readTr069Config(context.target().position());
        context.recordSnapshot(reading);

        assertThat(context.revalidation().revalidate(target).unchanged()).isTrue();
        assertThat(context.snapshot()).contains(reading);
        assertThat(context.olt().render(PlannedCommand.none(StepCode.P01))).isEqualTo("render RIOHACHA01 NONE");
        assertThatThrownBy(() -> context.recordSnapshot(reading)).hasMessageContaining("ya se tomó");
    }

    private static final class RecordingPort implements OltTr069ConfigPort {
        private final List<String> calls = new ArrayList<>();
        private int sessions;

        @Override
        public OltSessionHandle openSession(OltRef olt) {
            calls.add("open " + olt.codigo());
            return new OltSessionHandle("s-" + (++sessions), olt);
        }

        @Override
        public Tr069Reading readTr069Config(OltSessionHandle session, OnuPosition position) {
            calls.add("read " + session.sessionId() + " " + position.pon() + "/" + position.onuId());
            return Tr069Reading.withoutProfile("sin perfil");
        }

        @Override
        public CommandOutcome enterConfigMode(OltSessionHandle session) {
            calls.add("config " + session.sessionId());
            return CommandOutcome.accepted("config", "");
        }

        @Override
        public CommandOutcome enterBoard(OltSessionHandle session, BoardRef board) {
            calls.add("board " + session.sessionId() + " " + board);
            return CommandOutcome.accepted("interface gpon " + board, "");
        }

        @Override
        public CommandOutcome removeTr069Profile(OltSessionHandle session, OnuPosition position) {
            calls.add("undo " + session.sessionId() + " " + position.pon() + "/" + position.onuId());
            return CommandOutcome.accepted("undo", "");
        }

        @Override
        public CommandOutcome assignTr069Profile(OltSessionHandle session, OnuPosition position, Tr069ProfileName profile) {
            calls.add("assign " + session.sessionId() + " " + position.pon() + "/" + position.onuId() + " " + profile);
            return CommandOutcome.accepted("assign", "");
        }

        @Override
        public CommandOutcome saveConfiguration(OltSessionHandle session) {
            calls.add("save " + session.sessionId());
            return CommandOutcome.accepted("save", "");
        }

        @Override
        public void closeSession(OltSessionHandle session) {
            calls.add("close " + session.sessionId());
        }

        @Override
        public String render(OltRef olt, PlannedCommand command) {
            return "render " + olt.codigo() + " " + command.operation();
        }
    }
}
