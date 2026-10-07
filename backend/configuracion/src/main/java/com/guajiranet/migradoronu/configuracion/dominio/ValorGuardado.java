package com.guajiranet.migradoronu.configuracion.dominio;

import java.time.Instant;

/** Lo que hay en la base para una clave: el valor y quién lo cambió por última vez. */
public record ValorGuardado(Object valor, Long actualizadoPor, Instant actualizadoEn) {
}
