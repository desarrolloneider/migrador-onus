package com.guajiranet.migradoronu.ejecucion.puertos.salida;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.guajiranet.migradoronu.ejecucion.aplicacion.EjecucionVista;

/** Lecturas de ejecuciones_onu con su lote y su fila de la vista previa. */
public interface LecturasDeEjecucionPort {

    List<EjecucionVista> delLote(long loteId);

    Optional<EjecucionVista> porId(long ejecucionId);

    List<EjecucionVista> fallidasSinRevisar();

    /** Las fallidas sin revisar de los lotes que armó ese usuario (null: de todos). */
    default List<EjecucionVista> fallidasSinRevisar(Long creadoPor) {
        return fallidasSinRevisar();
    }

    /** Marca la fallida como revisada. Devuelve false si no era una fallida sin revisar. */
    boolean marcarRevisada(long ejecucionId, long usuarioId, String nota, Instant ahora);

    /** Marca como revisadas todas las fallidas sin revisar (las mismas de la pantalla Fallidas). Devuelve cuántas. */
    int marcarTodasRevisadas(long usuarioId, String nota, Instant ahora);

    /** Igual, solo las de los lotes que armó {@code creadoPor} (null: de todos). */
    default int marcarTodasRevisadas(long usuarioId, String nota, Instant ahora, Long creadoPor) {
        return marcarTodasRevisadas(usuarioId, nota, ahora);
    }
}
