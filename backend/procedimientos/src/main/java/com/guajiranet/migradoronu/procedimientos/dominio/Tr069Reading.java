package com.guajiranet.migradoronu.procedimientos.dominio;

import java.util.Objects;
import java.util.Optional;

/**
 * Lo que dice la OLT sobre la configuración TR-069 de una ONU, ya interpretado. Se guarda también el texto completo
 * para la fotografía previa y la auditoría (RN-09, RN-11).
 *
 * @param profile el perfil asignado, o {@code null} si la ONU no tiene perfil
 * @param rawText el texto exacto que devolvió la OLT
 */
public record Tr069Reading(Tr069ProfileName profile, String rawText) {

    public Tr069Reading {
        Objects.requireNonNull(rawText, "rawText");
    }

    public static Tr069Reading withProfile(Tr069ProfileName profile, String rawText) {
        return new Tr069Reading(Objects.requireNonNull(profile, "profile"), rawText);
    }

    public static Tr069Reading withoutProfile(String rawText) {
        return new Tr069Reading(null, rawText);
    }

    public Optional<Tr069ProfileName> currentProfile() {
        return Optional.ofNullable(profile);
    }

    public boolean hasProfile(Tr069ProfileName expected) {
        return expected.equals(profile);
    }
}
