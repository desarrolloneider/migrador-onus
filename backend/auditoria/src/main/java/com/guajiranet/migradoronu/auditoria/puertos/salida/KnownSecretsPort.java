package com.guajiranet.migradoronu.auditoria.puertos.salida;

import java.util.Collection;

/**
 * Los valores de las credenciales configuradas en el servidor (claves de SmartOLT, Wispro, OLT y base de datos), para
 * quitarlos de todo lo que se guarda en la auditoría (RN-25, RNF-06, arquitectura 10.3).
 */
public interface KnownSecretsPort {

    Collection<String> secretValues();
}
