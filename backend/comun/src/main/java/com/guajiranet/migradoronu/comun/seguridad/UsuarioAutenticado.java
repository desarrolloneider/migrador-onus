package com.guajiranet.migradoronu.comun.seguridad;

import java.io.Serializable;

/**
 * La persona de la sesión, tal como la guarda Spring Security en la sesión del servidor. Vive en comun para que los
 * controladores de cualquier módulo sepan quién hace cada acción (RN-11) sin depender del módulo acceso.
 */
public record UsuarioAutenticado(long id, String usuario, String nombre) implements Serializable {
}
