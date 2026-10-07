package com.guajiranet.migradoronu.ejecucion.dominio;

/** Una OLT por su código. El adaptador busca su dirección, puerto y protocolo en la configuración (PC-35). */
public record OltRef(String codigo) {

    public OltRef {
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("El código de la OLT es obligatorio");
        }
    }
}
