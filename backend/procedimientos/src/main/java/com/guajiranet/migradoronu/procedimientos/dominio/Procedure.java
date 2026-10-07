package com.guajiranet.migradoronu.procedimientos.dominio;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** La "receta" de una marca: sus pasos en orden, su versión y las preguntas de la OLT que se saben responder. */
public record Procedure(String brand, ProcedureVersion version, List<StepDefinition> steps,
                        List<KnownOltQuestion> knownQuestions) {

    public Procedure {
        if (brand == null || brand.isBlank()) {
            throw new IllegalArgumentException("La marca es obligatoria");
        }
        Objects.requireNonNull(version, "version");
        steps = List.copyOf(steps);
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("Un procedimiento tiene al menos un paso");
        }
        for (int i = 1; i < steps.size(); i++) {
            if (steps.get(i).code().compareTo(steps.get(i - 1).code()) <= 0) {
                throw new IllegalArgumentException("Los pasos deben ir en orden y sin repetirse: "
                        + steps.get(i - 1).code() + " seguido de " + steps.get(i).code());
            }
        }
        knownQuestions = knownQuestions == null ? List.of() : List.copyOf(knownQuestions);
    }

    public Optional<StepDefinition> step(StepCode code) {
        return steps.stream().filter(step -> step.code() == code).findFirst();
    }
}
