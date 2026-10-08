package com.guajiranet.migradoronu.ejecucion.puertos.salida;

import java.util.List;

import com.guajiranet.migradoronu.ejecucion.aplicacion.HistorialDeOnus.Filtros;
import com.guajiranet.migradoronu.ejecucion.aplicacion.HistorialDeOnus.Intento;
import com.guajiranet.migradoronu.ejecucion.aplicacion.HistorialDeOnus.Pagina;

/** Lecturas del historial por ONU, sobre ejecuciones_onu, lotes y lote_items. */
public interface HistorialDeOnusPort {

    Pagina buscar(Filtros filtros);

    /** Todos los intentos de un SN, también los simulados, del más reciente al más viejo. */
    List<Intento> intentos(String sn);
}
