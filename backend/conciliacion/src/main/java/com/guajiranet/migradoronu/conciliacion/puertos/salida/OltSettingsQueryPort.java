package com.guajiranet.migradoronu.conciliacion.puertos.salida;

import java.util.Set;

/** Tarjetas permitidas y acceso por consola de cada OLT (K8, RN-14). Lo implementa ensamblaje. */
public interface OltSettingsQueryPort {

    /** Vacío si la OLT no está registrada o no tiene tarjetas: ninguna de sus ONUs entra. */
    Set<String> allowedBoards(String oltCodigo);

    boolean consoleAccessConfigured(String oltCodigo);
}
