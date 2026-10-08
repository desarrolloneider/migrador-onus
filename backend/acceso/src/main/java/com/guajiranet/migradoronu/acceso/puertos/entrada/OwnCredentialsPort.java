package com.guajiranet.migradoronu.acceso.puertos.entrada;

import com.guajiranet.migradoronu.acceso.dominio.Usuario;

/** Credenciales propias del usuario con sesión, y el usuario inicial que viene del .env. */
public interface OwnCredentialsPort {

    /** Si es true, la pantalla no deja hacer nada más que cambiar la contraseña. */
    boolean debeCambiarClave(long usuarioId);

    /**
     * Cambia el nombre de usuario (opcional) y la contraseña. Exige la contraseña actual, una nueva de al menos 12
     * caracteres y distinta de la actual. Quita la marca de cambio obligatorio.
     */
    Usuario cambiar(long usuarioId, String usuarioNuevo, char[] claveActual, char[] claveNueva, String ipOrigen);

    /**
     * Al arrancar: si el usuario inicial no existe lo crea con esa contraseña y con cambio obligatorio. Si existe pero
     * nunca ha iniciado sesión, le pone esa contraseña y el cambio obligatorio. Si ya se usó, no lo toca.
     */
    void prepararUsuarioInicial(String usuario, char[] clave);
}
