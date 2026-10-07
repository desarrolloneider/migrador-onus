package com.guajiranet.migradoronu.lotes.puertos.salida;

import java.util.function.Supplier;

/** Una unidad de trabajo: todo o nada (arquitectura 7). La implementa la infraestructura con Spring. */
@FunctionalInterface
public interface TransactionPort {

    <T> T enTransaccion(Supplier<T> trabajo);
}
