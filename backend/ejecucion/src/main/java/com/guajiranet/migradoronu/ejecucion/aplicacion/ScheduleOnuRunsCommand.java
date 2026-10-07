package com.guajiranet.migradoronu.ejecucion.aplicacion;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.guajiranet.migradoronu.procedimientos.dominio.ProcedureVersion;

/** Las ONUs del grupo "entran" de un lote confirmado, en el orden de la vista previa (ESP-05). */
public record ScheduleOnuRunsCommand(long loteId, ProcedureVersion procedimientoVersion, List<Item> items) {

    public record Item(long loteItemId, String sn, int orden) {
        public Item {
            if (sn == null || sn.isBlank()) {
                throw new IllegalArgumentException("El SN es obligatorio");
            }
        }
    }

    public ScheduleOnuRunsCommand {
        Objects.requireNonNull(procedimientoVersion, "procedimientoVersion");
        items = List.copyOf(items);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Un lote confirmado tiene al menos una ONU en \"entran\" (spec 7.1)");
        }
        Set<String> seen = new HashSet<>();
        for (Item item : items) {
            if (!seen.add(item.sn())) {
                throw new IllegalArgumentException("La ONU " + item.sn() + " está dos veces en el lote " + loteId);
            }
        }
    }
}
