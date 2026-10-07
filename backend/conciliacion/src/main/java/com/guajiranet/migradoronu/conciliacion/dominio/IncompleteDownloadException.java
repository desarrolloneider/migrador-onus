package com.guajiranet.migradoronu.conciliacion.dominio;

/** La descarga de SmartOLT o Wispro quedó incompleta: no se arma la vista previa (RN-39). */
public class IncompleteDownloadException extends RuntimeException {

    private final String system;
    private final int page;
    private final int totalPages;

    public IncompleteDownloadException(String system, int page, int totalPages) {
        super("No se pudo armar la vista previa: la descarga de " + system + " se cortó en la página " + page + " de " + totalPages + ".");
        this.system = system;
        this.page = page;
        this.totalPages = totalPages;
    }

    public String system() {
        return system;
    }

    public int page() {
        return page;
    }

    public int totalPages() {
        return totalPages;
    }
}
