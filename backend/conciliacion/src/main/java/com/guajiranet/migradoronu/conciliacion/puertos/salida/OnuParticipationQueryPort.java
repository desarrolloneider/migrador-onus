package com.guajiranet.migradoronu.conciliacion.puertos.salida;

import java.util.Optional;

import com.guajiranet.migradoronu.conciliacion.dominio.UnreviewedFailure;

/** ¿La ONU está en otro lote activo? ¿Tiene una fallida sin revisar? (K10, K11). Lo implementa ensamblaje. */
public interface OnuParticipationQueryPort {

    /** El número del lote activo donde está la ONU (EXC_EN_OTRO_LOTE). */
    Optional<String> activeBatchNumberOf(String sn);

    Optional<UnreviewedFailure> unreviewedFailureOf(String sn);

    /**
     * Las ONUs que ya terminaron el paso 3 (Servicio WAN) en un lote real, con el número de ese lote. El paso 1 las deja
     * fuera: ponerles otra vez la VLAN 46 las devolvería a un estado a medias.
     */
    default java.util.Map<String, String> servicioWanTerminado() {
        return java.util.Map.of();
    }
}
