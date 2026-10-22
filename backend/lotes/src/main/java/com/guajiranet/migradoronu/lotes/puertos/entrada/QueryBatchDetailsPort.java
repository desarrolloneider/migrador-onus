package com.guajiranet.migradoronu.lotes.puertos.entrada;

import java.util.List;
import java.util.Optional;

import com.guajiranet.migradoronu.lotes.aplicacion.LoteDetalle;
import com.guajiranet.migradoronu.lotes.dominio.ClassifiedOnu;

/** El lote completo y su vista previa, para las pantallas (ESP-04, ESP-08). */
public interface QueryBatchDetailsPort {

    Optional<LoteDetalle> detalle(long loteId);

    List<LoteDetalle> recientes(int limite);

    /** Con {@code conArchivados}, también los lotes archivados. */
    default List<LoteDetalle> recientes(int limite, boolean conArchivados) {
        return recientes(limite);
    }

    /** Con {@code creadoPor}, solo los lotes que armó ese usuario (los no administradores solo ven los suyos). */
    default List<LoteDetalle> recientes(int limite, boolean conArchivados, Long creadoPor) {
        return recientes(limite, conArchivados).stream().filter(l -> creadoPor == null || l.creadoPor() == creadoPor).toList();
    }

    List<ClassifiedOnu> vistaPrevia(long loteId);

    /** Si no se puede ejecutar real, por qué (DEC-34: sin decidir PC-36 solo se simula). Vacío si se puede. */
    Optional<String> motivoSoloSimulacion();

    /** Tiempo estimado máximo por ONU, para estimar la duración del lote (ESP-04). */
    int minutosEstimadosPorOnu();
}
