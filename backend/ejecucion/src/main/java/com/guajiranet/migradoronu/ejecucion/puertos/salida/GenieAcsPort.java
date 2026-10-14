package com.guajiranet.migradoronu.ejecucion.puertos.salida;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.guajiranet.migradoronu.ejecucion.aplicacion.EstadoServicioWan;

/** GenieACS (su API, la de la interfaz web). Lo implementa ensamblaje. */
public interface GenieAcsPort {

    /** Lo que GenieACS tiene guardado de la ONU (puede estar viejo). Vacío si GenieACS no la tiene. */
    Optional<EstadoServicioWan> leer(String sn);

    /** Pide a la ONU esos parámetros en vivo (despertándola). true si la ONU respondió en el momento. */
    boolean refrescar(String idDispositivo, List<String> parametros);

    /** Manda el cambio de valores (despertándola). true si la ONU lo confirmó en el momento. No se repite a ciegas. */
    boolean cambiar(String idDispositivo, Map<String, String> valores);
}
