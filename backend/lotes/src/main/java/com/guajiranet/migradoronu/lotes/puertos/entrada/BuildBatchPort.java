package com.guajiranet.migradoronu.lotes.puertos.entrada;

import com.guajiranet.migradoronu.lotes.aplicacion.BatchView;
import com.guajiranet.migradoronu.lotes.aplicacion.BuildBatchCommand;
import com.guajiranet.migradoronu.lotes.aplicacion.BuildSingleOnuPreviewCommand;

/** Armar la vista previa de un lote o de una sola ONU (ESP-03, ESP-04, ESP-10). */
public interface BuildBatchPort {

    BatchView buildPreview(BuildBatchCommand command);

    BatchView buildSingleOnuPreview(BuildSingleOnuPreviewCommand command);
}
