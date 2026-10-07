package com.guajiranet.migradoronu.ejecucion.aplicacion;

import java.util.Objects;
import java.util.Optional;

import com.guajiranet.migradoronu.ejecucion.dominio.OltRef;
import com.guajiranet.migradoronu.ejecucion.puertos.salida.OltTr069ConfigPort;
import com.guajiranet.migradoronu.ejecucion.puertos.salida.OnuRevalidationPort;
import com.guajiranet.migradoronu.procedimientos.dominio.OltConsoleCapability;
import com.guajiranet.migradoronu.procedimientos.dominio.OnuTarget;
import com.guajiranet.migradoronu.procedimientos.dominio.RevalidationCapability;
import com.guajiranet.migradoronu.procedimientos.dominio.StepContext;
import com.guajiranet.migradoronu.procedimientos.dominio.Tr069ProfileName;
import com.guajiranet.migradoronu.procedimientos.dominio.Tr069Reading;

/**
 * Corrección 2b: la implementación de {@link StepContext} que entrega ejecucion a cada paso. Arma las capacidades
 * sobre sus propios puertos de salida, así los pasos de procedimientos nunca ven un puerto de otro módulo.
 */
public final class OnuStepContext implements StepContext {

    private final OnuTarget target;
    private final Tr069ProfileName targetProfile;
    private final OltConsoleCapability olt;
    private final RevalidationCapability revalidation;
    private Tr069Reading snapshot;

    public OnuStepContext(OnuTarget target, Tr069ProfileName targetProfile, OltConsoleCapability olt,
                          RevalidationCapability revalidation) {
        this.target = Objects.requireNonNull(target, "target");
        this.targetProfile = Objects.requireNonNull(targetProfile, "targetProfile");
        this.olt = Objects.requireNonNull(olt, "olt");
        this.revalidation = Objects.requireNonNull(revalidation, "revalidation");
    }

    public static OnuStepContext forOnu(OnuTarget target, Tr069ProfileName targetProfile, OltTr069ConfigPort oltPort,
                                        OnuRevalidationPort revalidationPort) {
        return new OnuStepContext(target, targetProfile,
                new SessionBoundOltConsole(oltPort, new OltRef(target.oltCodigo())),
                revalidationPort::revalidate);
    }

    /** Guarda la fotografía previa de P03. Se toma una sola vez por ejecución (RN-09). */
    public void recordSnapshot(Tr069Reading reading) {
        if (snapshot != null) {
            throw new IllegalStateException("La fotografía previa de " + target.sn() + " ya se tomó");
        }
        snapshot = Objects.requireNonNull(reading, "reading");
    }

    @Override
    public OnuTarget target() {
        return target;
    }

    @Override
    public Tr069ProfileName targetProfile() {
        return targetProfile;
    }

    @Override
    public Optional<Tr069Reading> snapshot() {
        return Optional.ofNullable(snapshot);
    }

    @Override
    public OltConsoleCapability olt() {
        return olt;
    }

    @Override
    public RevalidationCapability revalidation() {
        return revalidation;
    }
}
