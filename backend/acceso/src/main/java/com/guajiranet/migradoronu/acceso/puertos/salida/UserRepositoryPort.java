package com.guajiranet.migradoronu.acceso.puertos.salida;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.guajiranet.migradoronu.acceso.dominio.Usuario;

/** La tabla usuarios. No hay borrado: un usuario se desactiva. */
public interface UserRepositoryPort {

    Optional<Usuario> buscarPorUsuario(String usuario);

    Optional<Usuario> buscarPorId(long id);

    /** Guarda un usuario nuevo y devuelve su id. Si el usuario ya existe lanza un error de negocio. */
    long insertar(Usuario usuario);

    void actualizar(Usuario usuario);

    List<Usuario> listar();

    Map<Long, String> nombres(Collection<Long> ids);

    Set<Long> idsQueCoinciden(String texto);
}
