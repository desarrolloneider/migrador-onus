package com.guajiranet.migradoronu.acceso.dominio;

import java.util.EnumSet;
import java.util.Set;

/** Lo que puede hacer un usuario. Un administrador puede todo, incluida la gestión de usuarios. */
public record Permisos(boolean administrador, Set<Modulo> modulos) {

    public Permisos {
        modulos = modulos == null || modulos.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(modulos));
    }

    public static Permisos ninguno() {
        return new Permisos(false, Set.of());
    }

    public boolean puede(Modulo modulo) {
        return administrador || modulos.contains(modulo);
    }

    /** Los módulos efectivos: todos si es administrador. */
    public Set<Modulo> efectivos() {
        return administrador ? EnumSet.allOf(Modulo.class) : modulos;
    }
}
