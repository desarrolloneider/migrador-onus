package com.guajiranet.migradoronu.auditoria.dominio;

/** El último evento de la cadena: su id y su huella. Es lo que se escribe en un ancla. */
public record ChainHead(long id, String hash) {

    public ChainHead {
        Hashes.requireValid(hash, false, "hash");
    }
}
