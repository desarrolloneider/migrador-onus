package com.guajiranet.migradoronu.configuracion.dominio;

import java.util.Objects;

/** Una OLT registrada. Las credenciales no están aquí: viven en el servidor (arquitectura 11.1). */
public record OltSettings(Long id, String codigo, String nombre, String direccion, Integer puerto, ConsoleProtocol protocolo,
                          AllowedBoards tarjetas, boolean activa) {

    public OltSettings {
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("El código de la OLT es obligatorio");
        }
        if (puerto != null && (puerto < 1 || puerto > 65_535)) {
            throw new IllegalArgumentException("Puerto de consola inválido para " + codigo + ": " + puerto);
        }
        tarjetas = Objects.requireNonNullElse(tarjetas, AllowedBoards.none());
    }

    /** Si es {@code false}, sus ONUs quedan fuera con EXC_OLT_SIN_ACCESO_CONFIGURADO (K8). */
    public boolean consoleAccessConfigured() {
        return activa && direccion != null && !direccion.isBlank() && puerto != null && protocolo != null;
    }

    public OltSettings conTarjetas(AllowedBoards nuevas) {
        return new OltSettings(id, codigo, nombre, direccion, puerto, protocolo, nuevas, activa);
    }
}
