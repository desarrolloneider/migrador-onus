package com.guajiranet.migradoronu.procedimientos.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class ProcedureTest {

    private static final ProcedureVersion V1 = new ProcedureVersion("huawei-cli-1");

    @Test
    void aceptaLosPasosEnOrden() {
        Procedure procedure = new Procedure("HUAWEI", V1, List.of(step(StepCode.P01), step(StepCode.P02), step(StepCode.P08)), null);

        assertThat(procedure.step(StepCode.P02)).isPresent();
        assertThat(procedure.step(StepCode.P05)).isEmpty();
        assertThat(procedure.knownQuestions()).isEmpty();
    }

    @Test
    void rechazaPasosDesordenadosORepetidos() {
        assertThatThrownBy(() -> new Procedure("HUAWEI", V1, List.of(step(StepCode.P02), step(StepCode.P01)), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("P02 seguido de P01");
        assertThatThrownBy(() -> new Procedure("HUAWEI", V1, List.of(step(StepCode.P03), step(StepCode.P03)), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unaPreguntaConocidaSeReconocePorSuPatron() {
        KnownOltQuestion question = new KnownOltQuestion("Are you sure.*\\(y/n\\)", "y");

        assertThat(question.matches("  Are you sure to release the TR069 profile? (y/n)[n]:")).isTrue();
        assertThat(question.matches("{ <cr>|ontid<U><0,127> }:")).isFalse();
    }

    private static StepDefinition step(StepCode code) {
        return new StepDefinition() {
            @Override public StepCode code() { return code; }
            @Override public String name() { return code.displayName(); }
            @Override public boolean writes() { return false; }
            @Override public Observation observe(StepContext context) { return Observation.none(); }
            @Override public boolean isAlreadyDone(Observation observation) { return false; }
            @Override public PlannedCommand plan(StepContext context, Observation observation) { return PlannedCommand.none(code); }
            @Override public CommandOutcome act(StepContext context, PlannedCommand command) { return CommandOutcome.noCommand(); }
            @Override public Verification verify(StepContext context, PlannedCommand command) { return Verification.ok("", ""); }
        };
    }
}
