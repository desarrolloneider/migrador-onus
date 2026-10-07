package com.guajiranet.migradoronu.lotes.dominio;

import java.util.List;
import java.util.Objects;

/** Una ONU ya clasificada, tal como la necesita lotes para guardar la vista previa (columnas de lote_items). */
public record ClassifiedOnu(String sn, String ip, ItemGroup grupo, String codigoMotivo, String mensaje,
                            String cliente, String contrato, String marca, String modelo, String oltCodigo,
                            String tarjeta, int pon, int onuId, String perfilActual,
                            List<DuplicateEntry> registrosDuplicados) {

    public ClassifiedOnu {
        if (sn == null || sn.isBlank()) {
            throw new IllegalArgumentException("El SN es obligatorio");
        }
        Objects.requireNonNull(grupo, "grupo");
        if ((grupo == ItemGroup.EXCLUIDA) != (codigoMotivo != null)) {
            throw new IllegalArgumentException("Solo y toda ONU excluida lleva código de motivo: " + sn);
        }
        registrosDuplicados = registrosDuplicados == null ? List.of() : List.copyOf(registrosDuplicados);
    }
}
