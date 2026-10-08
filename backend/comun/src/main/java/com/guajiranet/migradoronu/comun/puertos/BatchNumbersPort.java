package com.guajiranet.migradoronu.comun.puertos;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Puerto transversal: el número visible de un lote (LOT-2026-000045) y su id. Varias tablas guardan solo el id; para
 * mostrarlo o buscar por número se pregunta aquí. Lo implementa ensamblaje con lotes.
 */
public interface BatchNumbersPort {

    Map<Long, String> numeros(Collection<Long> ids);

    Optional<Long> idDe(String numero);
}
