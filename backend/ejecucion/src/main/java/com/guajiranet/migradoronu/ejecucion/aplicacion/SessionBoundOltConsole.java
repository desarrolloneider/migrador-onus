package com.guajiranet.migradoronu.ejecucion.aplicacion;

import java.util.Objects;

import com.guajiranet.migradoronu.ejecucion.dominio.OltRef;
import com.guajiranet.migradoronu.ejecucion.dominio.OltSessionHandle;
import com.guajiranet.migradoronu.ejecucion.puertos.salida.OltTr069ConfigPort;
import com.guajiranet.migradoronu.procedimientos.dominio.BoardRef;
import com.guajiranet.migradoronu.procedimientos.dominio.CommandOutcome;
import com.guajiranet.migradoronu.procedimientos.dominio.OltConsoleCapability;
import com.guajiranet.migradoronu.procedimientos.dominio.OnuPosition;
import com.guajiranet.migradoronu.procedimientos.dominio.PlannedCommand;
import com.guajiranet.migradoronu.procedimientos.dominio.Tr069ProfileName;
import com.guajiranet.migradoronu.procedimientos.dominio.Tr069Reading;

/**
 * Corrección 2b: une el puerto de la OLT, que trabaja con {@link OltSessionHandle}, con la capacidad que ven los pasos,
 * que no conoce sesiones. Una instancia por ONU: guarda la sesión que abre P02 y la usa hasta P08 (RN-34).
 * No es segura para varios hilos; una ONU se ejecuta en un solo hilo.
 */
public final class SessionBoundOltConsole implements OltConsoleCapability {

    private final OltTr069ConfigPort port;
    private final OltRef olt;
    private OltSessionHandle session;

    public SessionBoundOltConsole(OltTr069ConfigPort port, OltRef olt) {
        this.port = Objects.requireNonNull(port, "port");
        this.olt = Objects.requireNonNull(olt, "olt");
    }

    public boolean isOpen() {
        return session != null;
    }

    @Override
    public CommandOutcome openSession() {
        if (session != null) {
            throw new IllegalStateException("Ya hay una sesión abierta en " + olt.codigo() + " para esta ONU (RN-34)");
        }
        session = port.openSession(olt);
        return CommandOutcome.accepted("(abrir sesión en " + olt.codigo() + ")", "");
    }

    @Override
    public Tr069Reading readTr069Config(OnuPosition position) {
        return port.readTr069Config(requireSession(), position);
    }

    @Override
    public CommandOutcome enterConfigMode() {
        return port.enterConfigMode(requireSession());
    }

    @Override
    public CommandOutcome enterBoard(BoardRef board) {
        return port.enterBoard(requireSession(), board);
    }

    @Override
    public CommandOutcome removeTr069Profile(OnuPosition position) {
        return port.removeTr069Profile(requireSession(), position);
    }

    @Override
    public CommandOutcome assignTr069Profile(OnuPosition position, Tr069ProfileName profile) {
        return port.assignTr069Profile(requireSession(), position, profile);
    }

    @Override
    public CommandOutcome saveConfiguration() {
        return port.saveConfiguration(requireSession());
    }

    @Override
    public CommandOutcome closeSession() {
        if (session == null) {
            return CommandOutcome.noCommand();
        }
        OltSessionHandle closing = session;
        session = null;
        port.closeSession(closing);
        return CommandOutcome.accepted("(cerrar sesión en " + olt.codigo() + ")", "");
    }

    @Override
    public String render(PlannedCommand command) {
        return port.render(olt, command);
    }

    private OltSessionHandle requireSession() {
        if (session == null) {
            throw new IllegalStateException("No hay sesión abierta en " + olt.codigo() + ": P02 debe ir antes");
        }
        return session;
    }
}
