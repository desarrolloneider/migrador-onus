package com.guajiranet.migradoronu.ensamblaje.ejecucion;

import org.springframework.stereotype.Component;

import com.guajiranet.migradoronu.conciliacion.aplicacion.RevalidateOnuCommand;
import com.guajiranet.migradoronu.conciliacion.aplicacion.RevalidationOutcome;
import com.guajiranet.migradoronu.conciliacion.dominio.OnuPosition;
import com.guajiranet.migradoronu.conciliacion.puertos.entrada.RevalidateOnuPort;
import com.guajiranet.migradoronu.ejecucion.puertos.salida.OnuRevalidationPort;
import com.guajiranet.migradoronu.procedimientos.dominio.OnuTarget;
import com.guajiranet.migradoronu.procedimientos.dominio.RevalidationResult;

/**
 * P01: ejecucion revalida en conciliacion. Antes (arquitectura v3.0) el paso RevalidateStep usaba directamente un
 * puerto de ejecucion y procedimientos quedaba atado a ejecucion; ahora el paso solo ve RevalidationCapability y la
 * cadena es paso → StepContext (ejecucion) → OnuRevalidationPort → este adaptador → conciliacion (2a y 2b).
 */
@Component
public class OnuRevalidationModuleAdapter implements OnuRevalidationPort {

    private final RevalidateOnuPort revalidator;

    public OnuRevalidationModuleAdapter(RevalidateOnuPort revalidator) {
        this.revalidator = revalidator;
    }

    @Override
    public RevalidationResult revalidate(OnuTarget target) {
        OnuPosition position = new OnuPosition(target.oltCodigo(), target.board().toString(),
                target.position().pon(), target.position().onuId());
        RevalidationOutcome outcome = revalidator.revalidate(
                new RevalidateOnuCommand(target.sn(), target.ip(), target.contrato(), position));
        return outcome.unchanged()
                ? RevalidationResult.same()
                : RevalidationResult.changed(outcome.changedField(), outcome.before(), outcome.after());
    }
}
