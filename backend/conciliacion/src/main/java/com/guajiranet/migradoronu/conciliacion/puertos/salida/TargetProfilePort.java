package com.guajiranet.migradoronu.conciliacion.puertos.salida;

/** El perfil TR-069 que se asigna (PC-02): las ONUs que ya lo tienen van a "ya tienen el perfil". Lo implementa ensamblaje. */
public interface TargetProfilePort {

    String perfilDestino();
}
