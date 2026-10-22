package com.guajiranet.migradoronu.comun.seguridad;

import java.io.Serializable;

/**
 * La persona de la sesión, tal como la guarda Spring Security en la sesión del servidor. Vive en comun para que los
 * controladores de cualquier módulo sepan quién hace cada acción (RN-11) sin depender del módulo acceso.
 */
public record UsuarioAutenticado(long id, String usuario, String nombre) implements Serializable {

    /**
     * Atributo de la petición con {@code Boolean.TRUE} si quien la hace es administrador (lo pone el filtro de acceso en
     * cada petición). Sin él, se trata como usuario normal: solo ve sus propios lotes.
     */
    public static final String ATRIBUTO_ADMINISTRADOR = "migrador.administrador";

    /** @param valorDelAtributo {@code request.getAttribute(ATRIBUTO_ADMINISTRADOR)} */
    public static boolean esAdministrador(Object valorDelAtributo) {
        return Boolean.TRUE.equals(valorDelAtributo);
    }
}
