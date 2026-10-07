package com.guajiranet.migradoronu.lotes.aplicacion;

/** Armar la vista previa de una sola ONU, con las mismas reglas de un lote (ESP-10). */
public record BuildSingleOnuPreviewCommand(long usuarioId, String sn) {

    public BuildSingleOnuPreviewCommand {
        if (sn == null || sn.isBlank()) {
            throw new IllegalArgumentException("Escribe el SN de la ONU");
        }
        sn = sn.strip();
    }
}
