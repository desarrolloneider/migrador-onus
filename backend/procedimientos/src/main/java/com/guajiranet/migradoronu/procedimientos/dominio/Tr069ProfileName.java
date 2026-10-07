package com.guajiranet.migradoronu.procedimientos.dominio;

/** Nombre de un perfil TR-069 de la OLT. El que asigna la herramienta es siempre el configurado (RN-12). */
public record Tr069ProfileName(String value) {

    public Tr069ProfileName {
        if (value == null || value.isBlank() || !value.equals(value.strip()) || value.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Nombre de perfil TR-069 inválido: '" + value + "'");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
