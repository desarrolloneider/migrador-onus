package com.guajiranet.migradoronu.procedimientos.dominio;

/** Las operaciones que un paso puede planear sobre la consola de la OLT. */
public enum OltOperation {
    NONE(false),
    OPEN_SESSION(false),
    READ_TR069_CONFIG(false),
    ENTER_CONFIG_MODE(false),
    ENTER_BOARD(false),
    REMOVE_TR069_PROFILE(true),
    ASSIGN_TR069_PROFILE(true),
    SAVE_CONFIGURATION(true),
    CLOSE_SESSION(false);

    private final boolean changesOnu;

    OltOperation(boolean changesOnu) {
        this.changesOnu = changesOnu;
    }

    /** {@code true} si la operación cambia la configuración de la OLT. Nunca se reintenta sola (RN-18). */
    public boolean changesOnu() {
        return changesOnu;
    }
}
