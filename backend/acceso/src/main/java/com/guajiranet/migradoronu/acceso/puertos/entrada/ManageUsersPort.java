package com.guajiranet.migradoronu.acceso.puertos.entrada;

import java.util.List;

import com.guajiranet.migradoronu.acceso.dominio.Usuario;

/** Administrar usuarios por consola en el servidor (ESP-02, PC-13). Cada acción queda en la auditoría como "consola". */
public interface ManageUsersPort {

    Usuario crear(String usuario, String nombre, char[] clave);

    void cambiarClave(String usuario, char[] clave);

    void desactivar(String usuario);

    void reactivar(String usuario);

    List<Usuario> listar();
}
