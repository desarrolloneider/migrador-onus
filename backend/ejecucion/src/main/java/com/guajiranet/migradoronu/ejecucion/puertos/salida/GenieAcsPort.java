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

    /**
     * Borra los fallos (faults) que GenieACS tenga guardados de ese equipo. Con un fallo, GenieACS reintenta con esperas
     * cada vez más largas y lo nuevo espera detrás. Devuelve cuántos borró; si no se puede, 0.
     */
    default int limpiarFallos(String idDispositivo) {
        return 0;
    }

    /**
     * Pone o quita una etiqueta del equipo en GenieACS. Con {@code migrar_wan}, el preset "migrador_servicio_wan" de
     * GenieACS le hace el cambio de servicio WAN en cuanto la ONU se reporte. true si GenieACS lo aceptó.
     */
    default boolean etiquetar(String idDispositivo, String etiqueta, boolean poner) {
        return false;
    }

    /** Los fallos que GenieACS tiene ahora de ese equipo, como "canal: código mensaje". Vacío si no hay o no se pueden leer. */
    default List<String> fallos(String idDispositivo) {
        return List.of();
    }
}
