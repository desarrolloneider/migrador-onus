package com.guajiranet.migradoronu.configuracion.dominio;

import java.time.Instant;
import java.util.Objects;

/** Un parámetro con su valor vigente. Un valor null significa "sin valor" (por ejemplo, PC-36 sin decidir). */
public record Parametro(DefinicionParametro definicion, Object valor, Long actualizadoPor, Instant actualizadoEn) {

    public Parametro {
        Objects.requireNonNull(definicion, "definicion");
    }
}
