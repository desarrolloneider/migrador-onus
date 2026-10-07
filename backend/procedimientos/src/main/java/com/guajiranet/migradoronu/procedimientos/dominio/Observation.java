package com.guajiranet.migradoronu.procedimientos.dominio;

import java.util.Optional;

/** Lo que un paso leyó antes de actuar: una lectura TR-069 en la OLT, una revalidación, o nada. */
public record Observation(Tr069Reading reading, RevalidationResult revalidation) {

    public Observation {
        if (reading != null && revalidation != null) {
            throw new IllegalArgumentException("Una observación es una lectura TR-069 o una revalidación, no ambas");
        }
    }

    public static Observation none() {
        return new Observation(null, null);
    }

    public static Observation of(Tr069Reading reading) {
        return new Observation(reading, null);
    }

    public static Observation of(RevalidationResult revalidation) {
        return new Observation(null, revalidation);
    }

    public Optional<Tr069Reading> tr069() {
        return Optional.ofNullable(reading);
    }

    public Optional<RevalidationResult> revalidationResult() {
        return Optional.ofNullable(revalidation);
    }
}
