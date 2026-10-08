package com.guajiranet.migradoronu.acceso.aplicacion;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.guajiranet.migradoronu.acceso.dominio.Usuario;
import com.guajiranet.migradoronu.acceso.puertos.entrada.QueryUsersPort;
import com.guajiranet.migradoronu.acceso.puertos.salida.UserRepositoryPort;

public class ConsultarUsuariosUseCase implements QueryUsersPort {

    private final UserRepositoryPort usuarios;

    public ConsultarUsuariosUseCase(UserRepositoryPort usuarios) {
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
    }

    @Override
    public boolean activo(long usuarioId) {
        return usuarios.buscarPorId(usuarioId).map(Usuario::activo).orElse(false);
    }

    @Override
    public Map<Long, String> nombres(Collection<Long> ids) {
        return ids == null || ids.isEmpty() ? Map.of() : usuarios.nombres(ids);
    }

    @Override
    public Set<Long> idsQueCoinciden(String texto) {
        return texto == null || texto.isBlank() ? Set.of() : usuarios.idsQueCoinciden(texto.strip());
    }

    @Override
    public com.guajiranet.migradoronu.acceso.dominio.Permisos permisos(long usuarioId) {
        return usuarios.permisos(usuarioId);
    }
}
