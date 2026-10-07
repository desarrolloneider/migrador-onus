package com.guajiranet.migradoronu.auditoria.puertos.salida;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import com.guajiranet.migradoronu.auditoria.aplicacion.AuditPage;
import com.guajiranet.migradoronu.auditoria.aplicacion.AuditSearchCriteria;
import com.guajiranet.migradoronu.auditoria.dominio.ChainHead;
import com.guajiranet.migradoronu.auditoria.dominio.SealedAuditEvent;
import com.guajiranet.migradoronu.auditoria.dominio.StoredAuditEvent;

/** La tabla auditoria_eventos. Solo inserción y lectura: no existe ninguna operación para cambiar o borrar (10.2). */
public interface AuditRepositoryPort {

    /**
     * En una transacción propia y serializada con las demás inserciones: lee la huella del último evento, se la pasa
     * a {@code sealer}, guarda el evento sellado y confirma. Devuelve solo cuando quedó confirmado (RN-11). Si la base
     * no responde, lanza {@code AuditUnavailableException} (RN-30).
     */
    StoredAuditEvent append(Function<String, SealedAuditEvent> sealer);

    Optional<ChainHead> head();

    /** Eventos con {@code afterId < id <= upToId}, en orden de id. */
    List<StoredAuditEvent> readChain(long afterId, long upToId, int limit);

    AuditPage search(AuditSearchCriteria criteria);
}
