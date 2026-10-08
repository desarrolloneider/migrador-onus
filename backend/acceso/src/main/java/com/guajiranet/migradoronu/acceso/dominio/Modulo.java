package com.guajiranet.migradoronu.acceso.dominio;

/**
 * Las partes de la herramienta que se le pueden dar a un usuario. Inicio y su propia contraseña los tiene todo el que
 * entra; la gestión de usuarios es solo de los administradores.
 */
public enum Modulo {
    LOTES("Lotes", "Ver lotes, armar vistas previas y ver las ONUs de cada OLT."),
    EJECUTAR("Ejecutar cambios", "Simular y ejecutar cambios de perfil, reintentar y detener lotes."),
    HISTORIAL("Historial de ONUs", "Ver y exportar el último resultado de cada ONU."),
    FALLIDAS("Fallidas por revisar", "Ver las ONUs fallidas y marcarlas como revisadas."),
    AUDITORIA("Auditoría", "Ver, exportar y verificar la auditoría."),
    CONFIGURACION("Configuración", "Cambiar parámetros y tarjetas permitidas de las OLT.");

    private final String nombre;
    private final String descripcion;

    Modulo(String nombre, String descripcion) {
        this.nombre = nombre;
        this.descripcion = descripcion;
    }

    public String nombre() {
        return nombre;
    }

    public String descripcion() {
        return descripcion;
    }
}
