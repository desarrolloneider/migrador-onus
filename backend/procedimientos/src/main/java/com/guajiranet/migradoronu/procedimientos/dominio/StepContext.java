package com.guajiranet.migradoronu.procedimientos.dominio;

import java.util.Optional;

/**
 * Lo que un paso puede ver y usar mientras se ejecuta sobre una ONU.
 *
 * <p>Corrección 2b: el contrato lo define procedimientos y lo implementa ejecucion, que es quien tiene la sesión
 * en la OLT y los puertos hacia los demás módulos. La dependencia va en una sola dirección:
 * ejecucion → procedimientos.dominio (docs/adr/0003).
 */
public interface StepContext {

    /** Los datos de la ONU tal como quedaron en la vista previa confirmada. */
    OnuTarget target();

    /** El perfil configurado al confirmar el lote (RN-12, RN-37). */
    Tr069ProfileName targetProfile();

    /** La fotografía previa tomada en P03; vacía antes de P03 (RN-09). */
    Optional<Tr069Reading> snapshot();

    OltConsoleCapability olt();

    RevalidationCapability revalidation();
}
