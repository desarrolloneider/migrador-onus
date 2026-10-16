package com.guajiranet.migradoronu.lotes.puertos.salida;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.guajiranet.migradoronu.lotes.aplicacion.LoteDetalle;
import com.guajiranet.migradoronu.lotes.dominio.BatchFilters;
import com.guajiranet.migradoronu.lotes.dominio.BatchMode;
import com.guajiranet.migradoronu.lotes.dominio.BatchNumber;
import com.guajiranet.migradoronu.lotes.dominio.BatchStatus;
import com.guajiranet.migradoronu.lotes.dominio.ClassifiedOnu;
import com.guajiranet.migradoronu.lotes.dominio.ScheduledOnu;

/** Las tablas lotes y lote_items. Nada se borra: un lote descartado queda como dato (respuesta del 3 de octubre). */
public interface BatchRepositoryPort {

    long siguienteConsecutivo();

    /**
     * Guarda la vista previa con sus ONUs en el orden dado. {@code parametros} son los vigentes al armar, con su huella,
     * para comprobar RN-36 al confirmar.
     */
    long crearVistaPrevia(BatchNumber numero, BatchMode modo, BatchFilters filtros, long usuarioId, Instant ahora, Instant venceEn,
                          Map<String, Object> parametros, List<ClassifiedOnu> items);

    Optional<LoteDetalle> detalle(long loteId);

    /** Los últimos lotes; los archivados solo si {@code conArchivados}. */
    List<LoteDetalle> recientes(int limite, boolean conArchivados);

    default List<LoteDetalle> recientes(int limite) {
        return recientes(limite, false);
    }

    /** Archiva los lotes que ya no se mueven (terminados, detenidos, descartados, vistas previas vencidas). Devuelve sus números. */
    List<String> archivarTerminados(java.time.Instant ahora);

    List<ClassifiedOnu> items(long loteId);

    /** Las ONUs del grupo "entran", en orden. */
    List<ScheduledOnu> itemsQueEntran(long loteId);

    /** Bloquea la fila del lote hasta el final de la transacción (SELECT ... FOR UPDATE). */
    Optional<BatchStatus> estadoBloqueando(long loteId);

    Map<String, Object> parametrosDeVistaPrevia(long loteId);

    void confirmar(long loteId, long usuarioId, boolean simulacion, Instant ahora, Map<String, Object> parametros,
                   String procedimientoVersion);

    void cambiarEstado(long loteId, BatchStatus estado, Long usuarioId, String motivo, Instant ahora);
}
