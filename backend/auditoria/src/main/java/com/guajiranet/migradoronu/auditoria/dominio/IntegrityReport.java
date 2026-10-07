package com.guajiranet.migradoronu.auditoria.dominio;

/**
 * Resultado de verificar la auditoría (ESP-13).
 *
 * @param brokenEventId el primer evento donde se rompe la cadena; {@code null} si está íntegra
 */
public record IntegrityReport(boolean intact, long checkedEvents, Long brokenEventId, String reason, Long lastEventId,
                              String lastHash) {

    public static IntegrityReport broken(long checkedEvents, Long brokenEventId, String reason) {
        return new IntegrityReport(false, checkedEvents, brokenEventId, reason, null, null);
    }

    public String message() {
        if (intact) {
            return "La auditoría está íntegra: se revisaron " + checkedEvents + " eventos.";
        }
        return "La auditoría NO está íntegra"
                + (brokenEventId == null ? "" : " desde el evento " + brokenEventId)
                + ": " + reason;
    }
}
