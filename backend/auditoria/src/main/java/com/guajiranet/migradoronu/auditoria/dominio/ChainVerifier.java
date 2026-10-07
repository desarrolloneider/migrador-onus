package com.guajiranet.migradoronu.auditoria.dominio;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Recorre la cadena en orden de id y se detiene en la primera alteración (ESP-13, RNF-02). Detecta:
 * <ul>
 *   <li>un evento modificado: su contenido ya no da su huella;</li>
 *   <li>un evento quitado o metido entre otros: la huella anterior no coincide con el evento previo;</li>
 *   <li>eventos quitados del principio: el primero dice tener un anterior;</li>
 *   <li>eventos quitados del final o la cadena reescrita: un ancla no aparece o no coincide.</li>
 * </ul>
 */
public final class ChainVerifier {

    private final TreeMap<Long, String> pendingAnchors = new TreeMap<>();
    private String previousHash;
    private long checked;
    private Long lastId;
    private Long brokenId;
    private String reason;

    public ChainVerifier(List<AuditAnchor> anchors) {
        Map<Long, String> seen = new HashMap<>();
        for (AuditAnchor anchor : anchors) {
            String other = seen.putIfAbsent(anchor.eventId(), anchor.hash());
            if (other != null && !other.equals(anchor.hash())) {
                fail(anchor.eventId(), "El archivo de anclas tiene dos huellas distintas para el mismo evento");
            }
        }
        pendingAnchors.putAll(seen);
    }

    /** Revisa el siguiente evento. Devuelve {@code false} si la cadena está rota y no tiene sentido seguir. */
    public boolean accept(StoredAuditEvent row) {
        if (isBroken()) {
            return false;
        }
        checked++;
        lastId = row.id();
        if (!row.isReadable()) {
            return fail(row.id(), "El evento no se puede interpretar (" + row.unreadableReason() + ")");
        }
        if (!Objects.equals(row.previousHash(), previousHash)) {
            return fail(row.id(), previousHash == null
                    ? "El primer evento dice tener un evento anterior: se quitaron eventos del principio"
                    : "Su huella anterior no coincide con el evento previo: se quitó, se agregó o se alteró un evento");
        }
        if (!HashChain.hashOf(previousHash, row.event()).equals(row.hash())) {
            return fail(row.id(), "Su contenido ya no corresponde a su huella: el evento se modificó");
        }
        String anchored = pendingAnchors.remove(row.id());
        if (anchored != null && !anchored.equals(row.hash())) {
            return fail(row.id(), "Su huella no coincide con el ancla guardada fuera de la base: la cadena se reescribió");
        }
        previousHash = row.hash();
        return true;
    }

    public IntegrityReport finish() {
        if (!isBroken() && !pendingAnchors.isEmpty()) {
            long missing = pendingAnchors.firstKey();
            fail(missing, "Hay un ancla para el evento " + missing + " y ese evento ya no está: se borraron eventos");
        }
        if (isBroken()) {
            return new IntegrityReport(false, checked, brokenId, reason, lastId, null);
        }
        return new IntegrityReport(true, checked, null, null, lastId, previousHash);
    }

    private boolean isBroken() {
        return reason != null;
    }

    private boolean fail(long id, String why) {
        if (!isBroken()) {
            brokenId = id;
            reason = why;
        }
        return false;
    }
}
