package com.guajiranet.migradoronu.ensamblaje.lotes;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.guajiranet.migradoronu.comun.puertos.BatchNumbersPort;
import com.guajiranet.migradoronu.lotes.aplicacion.BatchView;
import com.guajiranet.migradoronu.lotes.puertos.entrada.QueryBatchPort;

/** Números de lote para quien solo guarda el id (auditoría, historial). */
@Component
public class BatchNumbersModuleAdapter implements BatchNumbersPort {

    private final QueryBatchPort lotes;

    public BatchNumbersModuleAdapter(QueryBatchPort lotes) {
        this.lotes = lotes;
    }

    @Override
    public Map<Long, String> numeros(Collection<Long> ids) {
        Map<Long, String> numeros = new LinkedHashMap<>();
        ids.forEach(id -> lotes.findById(id).ifPresent(l -> numeros.put(id, l.numero())));
        return numeros;
    }

    @Override
    public Optional<Long> idDe(String numero) {
        return numero == null || numero.isBlank() ? Optional.empty() : lotes.findByNumero(numero).map(BatchView::id);
    }
}
