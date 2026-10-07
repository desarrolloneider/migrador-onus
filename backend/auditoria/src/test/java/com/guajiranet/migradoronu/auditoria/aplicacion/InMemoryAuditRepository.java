package com.guajiranet.migradoronu.auditoria.aplicacion;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import com.guajiranet.migradoronu.auditoria.dominio.ChainHead;
import com.guajiranet.migradoronu.auditoria.dominio.SealedAuditEvent;
import com.guajiranet.migradoronu.auditoria.dominio.StoredAuditEvent;
import com.guajiranet.migradoronu.auditoria.puertos.salida.AuditRepositoryPort;

/** Repositorio en memoria para probar los casos de uso sin base de datos. */
class InMemoryAuditRepository implements AuditRepositoryPort {

    final List<StoredAuditEvent> rows = new ArrayList<>();

    @Override
    public synchronized StoredAuditEvent append(Function<String, SealedAuditEvent> sealer) {
        String previous = rows.isEmpty() ? null : rows.getLast().hash();
        SealedAuditEvent sealed = sealer.apply(previous);
        StoredAuditEvent stored = StoredAuditEvent.readable(rows.size() + 1, sealed.previousHash(), sealed.hash(), sealed.event());
        rows.add(stored);
        return stored;
    }

    @Override
    public Optional<ChainHead> head() {
        return rows.isEmpty() ? Optional.empty() : Optional.of(new ChainHead(rows.getLast().id(), rows.getLast().hash()));
    }

    @Override
    public List<StoredAuditEvent> readChain(long afterId, long upToId, int limit) {
        return rows.stream().filter(r -> r.id() > afterId && r.id() <= upToId).limit(limit).toList();
    }

    @Override
    public AuditPage search(AuditSearchCriteria criteria) {
        return new AuditPage(rows, rows.size(), criteria.page(), criteria.size());
    }
}
