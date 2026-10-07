package com.guajiranet.migradoronu.acceso.puertos.salida;

import com.guajiranet.migradoronu.acceso.dominio.PoliticaDeAcceso;

/** La política vigente, leída de los parámetros de configuración (RNF-11). Lo implementa ensamblaje. */
public interface AccessPolicyPort {

    PoliticaDeAcceso politica();
}
