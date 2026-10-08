package com.guajiranet.migradoronu.ensamblaje.transversal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.guajiranet.migradoronu.auditoria.aplicacion.AuditPage;
import com.guajiranet.migradoronu.auditoria.aplicacion.AuditSearchCriteria;
import com.guajiranet.migradoronu.auditoria.dominio.AuditEvent;
import com.guajiranet.migradoronu.auditoria.dominio.StoredAuditEvent;
import com.guajiranet.migradoronu.auditoria.puertos.entrada.QueryAuditPort;
import com.guajiranet.migradoronu.comun.puertos.EventoDeEjecucion;
import com.guajiranet.migradoronu.comun.puertos.RunTimelinePort;

/** La línea de tiempo de una ejecución sale de la auditoría, filtrada por lote y SN y luego por ejecución. */
@Component
public class RunTimelineModuleAdapter implements RunTimelinePort {

    private final QueryAuditPort auditoria;

    public RunTimelineModuleAdapter(QueryAuditPort auditoria) {
        this.auditoria = auditoria;
    }

    @Override
    public List<EventoDeEjecucion> deEjecucion(long ejecucionId, long loteId, String sn) {
        List<EventoDeEjecucion> eventos = new ArrayList<>();
        for (int pagina = 0; pagina < 10; pagina++) {
            AuditPage p = auditoria.search(new AuditSearchCriteria(null, null, null, loteId, sn, null, null, null, null, pagina,
                    AuditSearchCriteria.MAX_PAGE_SIZE, false));
            p.events().stream().filter(StoredAuditEvent::isReadable).map(StoredAuditEvent::event)
                    .filter(e -> Objects.equals(e.ejecucionId(), ejecucionId))
                    .map(RunTimelineModuleAdapter::evento).forEach(eventos::add);
            if (p.events().size() < AuditSearchCriteria.MAX_PAGE_SIZE) {
                break;
            }
        }
        return eventos;
    }

    private static EventoDeEjecucion evento(AuditEvent e) {
        Object comando = e.peticion() == null ? null : e.peticion().get("comando");
        Object respuesta = e.respuesta() == null ? null : e.respuesta().get("texto");
        return new EventoDeEjecucion(e.ocurridoEn(), e.accion().name(), e.paso(), e.resultado(), e.codigo(), e.mensaje(),
                comando == null ? null : comando.toString(), respuesta == null ? null : respuesta.toString());
    }
}
