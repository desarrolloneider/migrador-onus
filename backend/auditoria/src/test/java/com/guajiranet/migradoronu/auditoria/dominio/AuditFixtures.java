package com.guajiranet.migradoronu.auditoria.dominio;

import java.time.Instant;
import java.util.Map;

import com.guajiranet.migradoronu.comun.puertos.ActorType;
import com.guajiranet.migradoronu.comun.puertos.AuditAction;

public final class AuditFixtures {

    public static final Instant T0 = Instant.parse("2026-10-05T15:00:00.123456Z");

    private AuditFixtures() {
    }

    public static AuditEvent event(String mensaje) {
        return event(T0, mensaje);
    }

    public static AuditEvent event(Instant when, String mensaje) {
        return new AuditEvent(when, ActorType.PROCESO_EJECUTOR, 7L, null, AuditAction.COMANDO_RESPONDIDO, 45L, 812L,
                "HWTC1A2B3C4D", "P05", "OLT RIOHACHA01", Map.of("comando", "undo ont tr069-server-config 0 33"),
                Map.of("texto", "MA5800(config-if-gpon-0/1)#", "duracionMs", 412), "OK", null, mensaje, 412L);
    }

    /** Una cadena válida de {@code n} eventos, con ids desde 1. */
    public static java.util.List<StoredAuditEvent> chain(int n) {
        java.util.List<StoredAuditEvent> rows = new java.util.ArrayList<>();
        String previous = null;
        for (int i = 1; i <= n; i++) {
            SealedAuditEvent sealed = HashChain.seal(previous, event(T0.plusSeconds(i), "evento " + i));
            rows.add(StoredAuditEvent.readable(i, sealed.previousHash(), sealed.hash(), sealed.event()));
            previous = sealed.hash();
        }
        return rows;
    }
}
