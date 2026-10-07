package com.guajiranet.migradoronu.procedimientos.dominio;

import java.util.Objects;

/**
 * Lo que un paso decidió mandar, como intención. El texto exacto lo arma el adaptador de la OLT con sus plantillas
 * ({@link OltConsoleCapability#render(PlannedCommand)}), para que cerrar PC-33, PC-34 o PC-36 sea cambiar una
 * plantilla y no un paso.
 */
public record PlannedCommand(StepCode step, OltOperation operation, OnuPosition position, BoardRef board,
                             Tr069ProfileName profile) {

    public PlannedCommand {
        Objects.requireNonNull(step, "step");
        Objects.requireNonNull(operation, "operation");
        switch (operation) {
            case ENTER_BOARD -> Objects.requireNonNull(board, "ENTER_BOARD necesita la tarjeta");
            case READ_TR069_CONFIG, REMOVE_TR069_PROFILE -> Objects.requireNonNull(position, operation + " necesita puerto e ID");
            case ASSIGN_TR069_PROFILE -> {
                Objects.requireNonNull(position, "ASSIGN_TR069_PROFILE necesita puerto e ID");
                Objects.requireNonNull(profile, "ASSIGN_TR069_PROFILE necesita el perfil");
            }
            default -> { }
        }
    }

    public static PlannedCommand none(StepCode step) {
        return new PlannedCommand(step, OltOperation.NONE, null, null, null);
    }

    public static PlannedCommand of(StepCode step, OltOperation operation) {
        return new PlannedCommand(step, operation, null, null, null);
    }

    public static PlannedCommand enterBoard(StepCode step, BoardRef board) {
        return new PlannedCommand(step, OltOperation.ENTER_BOARD, null, board, null);
    }

    public static PlannedCommand onOnu(StepCode step, OltOperation operation, OnuPosition position) {
        return new PlannedCommand(step, operation, position, null, null);
    }

    public static PlannedCommand assignProfile(StepCode step, OnuPosition position, Tr069ProfileName profile) {
        return new PlannedCommand(step, OltOperation.ASSIGN_TR069_PROFILE, position, null, profile);
    }

    public boolean isNone() {
        return operation == OltOperation.NONE;
    }
}
