package com.guajiranet.migradoronu.ejecucion.puertos.salida;

import com.guajiranet.migradoronu.ejecucion.dominio.OltRef;
import com.guajiranet.migradoronu.ejecucion.dominio.OltSessionHandle;
import com.guajiranet.migradoronu.procedimientos.dominio.BoardRef;
import com.guajiranet.migradoronu.procedimientos.dominio.CommandOutcome;
import com.guajiranet.migradoronu.procedimientos.dominio.OnuPosition;
import com.guajiranet.migradoronu.procedimientos.dominio.PlannedCommand;
import com.guajiranet.migradoronu.procedimientos.dominio.Tr069ProfileName;
import com.guajiranet.migradoronu.procedimientos.dominio.Tr069Reading;

/**
 * La consola de la OLT, en términos de intención y no de texto de consola (arquitectura 9.5). Lo implementan
 * HuaweiOltConsoleAdapter y SimulatedOltConsoleAdapter.
 *
 * <p>Todo error se lanza como {@code StepFailure} con su código del catálogo 8.3 ({@code CredentialsRejectedException}
 * si la OLT rechaza las credenciales). Los comandos de cambio nunca se reintentan (RN-18).
 */
public interface OltTr069ConfigPort {

    OltSessionHandle openSession(OltRef olt);

    Tr069Reading readTr069Config(OltSessionHandle session, OnuPosition position);

    CommandOutcome enterConfigMode(OltSessionHandle session);

    CommandOutcome enterBoard(OltSessionHandle session, BoardRef board);

    CommandOutcome removeTr069Profile(OltSessionHandle session, OnuPosition position);

    CommandOutcome assignTr069Profile(OltSessionHandle session, OnuPosition position, Tr069ProfileName profile);

    CommandOutcome saveConfiguration(OltSessionHandle session);

    void closeSession(OltSessionHandle session);

    /** Texto exacto que se mandaría a esa OLT, armado con las plantillas del procedimiento, sin mandarlo. */
    String render(OltRef olt, PlannedCommand command);
}
