package com.guajiranet.migradoronu.ejecucion.puertos.salida;

/** Abre una sesión con la consola de una OLT registrada. Lo implementa ensamblaje con configuracion e integraciones. */
public interface OltConsoleFactoryPort {

    OltConsolePort abrir(String oltCodigo);
}
