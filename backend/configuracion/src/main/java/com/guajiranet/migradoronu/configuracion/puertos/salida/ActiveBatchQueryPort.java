package com.guajiranet.migradoronu.configuracion.puertos.salida;

import java.util.Optional;

/** ¿Hay un lote en ejecución? Si lo hay, la configuración no se cambia (RN-28). Lo implementa ensamblaje. */
public interface ActiveBatchQueryPort {

    Optional<String> runningBatchNumber();
}
