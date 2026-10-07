package com.guajiranet.migradoronu.procedimientos.dominio;

/**
 * Un paso del procedimiento (arquitectura 9.3). El motor de pasos de ejecucion llama a estos métodos en orden y
 * registra cada punto en la auditoría antes de seguir.
 *
 * <p>Corrección 2b: un paso solo ve tipos de {@code procedimientos.dominio}. Todo lo que necesita del mundo (la
 * consola de la OLT, la revalidación en SmartOLT y Wispro) lo pide a {@link StepContext}, que implementa ejecucion.
 * Así procedimientos no depende de ningún otro módulo (docs/adr/0003).
 *
 * <p>Los fallos no se devuelven: la capacidad que falla lanza la excepción del motor y el paso no la atrapa.
 */
public interface StepDefinition {

    StepCode code();

    String name();

    /** {@code true} si el paso cambia algo en la ONU. En simulación esos pasos no se mandan (RN-19). */
    boolean writes();

    /** Lectura del estado actual antes de actuar. */
    Observation observe(StepContext context);

    /** Si la ONU ya está en el estado buscado, el paso queda "ya estaba" y no se manda nada (RN-27). */
    boolean isAlreadyDone(Observation observation);

    /** Qué se va a mandar. El motor registra su texto exacto (COMANDO_PLANEADO) antes de mandarlo. */
    PlannedCommand plan(StepContext context, Observation observation);

    CommandOutcome act(StepContext context, PlannedCommand command);

    /** Lectura que confirma que el cambio quedó aplicado. Que la OLT acepte el comando no basta (RN-10). */
    Verification verify(StepContext context, PlannedCommand command);
}
