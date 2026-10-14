package com.guajiranet.migradoronu.ejecucion.aplicacion;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Los datos del historial por ONU (ESP-14). El último resultado de cada SN sale de los lotes reales: CORREGIDA,
 * FALLIDA, EXCLUIDA o YA_TENIA_PERFIL. Las simulaciones no corrigen nada, pero sí aparecen entre los intentos.
 */
public final class HistorialDeOnus {

    private HistorialDeOnus() {
    }

    public record Filtros(String tipo, String sn, String olt, String tarjeta, String resultado, Instant desde, Instant hasta, int pagina, int tamano) {
    }

    public record Entrada(String sn, String ip, String olt, String tarjeta, Integer pon, Integer onuId, String ultimoResultado, Boolean revisada,
                          String codigo, String motivo, long loteId, String loteNumero, Instant actualizadoEn) {
    }

    public record Pagina(List<Entrada> entradas, long total, int pagina, int tamano, Map<String, Long> totales) {
    }

    public record Intento(Long ejecucionId, long loteId, String loteNumero, Instant fecha, String resultado, boolean simulacion,
                          String paso, String codigo, String mensaje, String tipo) {
    }
}
