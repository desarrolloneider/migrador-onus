package com.guajiranet.migradoronu.procedimientos.dominio;

/**
 * Lo que un paso puede pedirle a la consola de la OLT de su ONU, en términos de intención (RNF-16). La sesión ya
 * está ligada a esa OLT: el paso no conoce sesiones, transportes ni textos de consola.
 *
 * <p>La implementa ejecucion sobre su puerto {@code OltTr069ConfigPort} (corrección 2b). Cualquier fallo de la OLT
 * llega como excepción del motor con su código del catálogo 8.3 de la spec; nunca como un resultado que se pueda
 * ignorar (RN-06).
 */
public interface OltConsoleCapability {

    CommandOutcome openSession();

    /** Lectura de la configuración TR-069 de la ONU (comando en PC-34). Si el texto no se reconoce, falla. */
    Tr069Reading readTr069Config(OnuPosition position);

    CommandOutcome enterConfigMode();

    CommandOutcome enterBoard(BoardRef board);

    CommandOutcome removeTr069Profile(OnuPosition position);

    CommandOutcome assignTr069Profile(OnuPosition position, Tr069ProfileName profile);

    CommandOutcome saveConfiguration();

    /** Cierra la sesión si está abierta. Se puede llamar aunque nunca se haya abierto. */
    CommandOutcome closeSession();

    /** Texto exacto que se mandaría para este comando, sin mandarlo (COMANDO_PLANEADO y simulación). */
    String render(PlannedCommand command);
}
