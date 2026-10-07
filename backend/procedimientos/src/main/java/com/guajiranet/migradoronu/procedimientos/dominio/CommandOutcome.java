package com.guajiranet.migradoronu.procedimientos.dominio;

import java.util.Objects;

/**
 * Lo que pasó al mandar un comando que la OLT aceptó. Los rechazos, preguntas desconocidas, tiempos agotados y
 * cortes no llegan aquí: el adaptador los traduce a la excepción del motor con su código (arquitectura 9.3).
 *
 * @param commandText el texto exacto mandado, sin credenciales
 * @param rawResponse el texto completo que devolvió la OLT
 */
public record CommandOutcome(Kind kind, String commandText, String rawResponse) {

    public enum Kind {
        /** La OLT devolvió el prompt esperado sin texto de error. Falta verificar leyendo (RN-10). */
        ACEPTADO,
        /** No se mandó: el lote es una simulación (RN-19). */
        SIMULADO,
        /** El paso no manda ningún comando a la OLT. */
        SIN_COMANDO
    }

    public CommandOutcome {
        Objects.requireNonNull(kind, "kind");
        commandText = commandText == null ? "" : commandText;
        rawResponse = rawResponse == null ? "" : rawResponse;
    }

    public static CommandOutcome accepted(String commandText, String rawResponse) {
        return new CommandOutcome(Kind.ACEPTADO, commandText, rawResponse);
    }

    public static CommandOutcome simulated(String commandText) {
        return new CommandOutcome(Kind.SIMULADO, commandText, "");
    }

    public static CommandOutcome noCommand() {
        return new CommandOutcome(Kind.SIN_COMANDO, "", "");
    }
}
