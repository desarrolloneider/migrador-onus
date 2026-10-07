package com.guajiranet.migradoronu.ensamblaje.acceso;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.guajiranet.migradoronu.acceso.puertos.entrada.QueryUsersPort;
import com.guajiranet.migradoronu.comun.puertos.UserNamesPort;

/** Los demás módulos muestran quién hizo algo preguntando a acceso. */
@Component
public class UserNamesModuleAdapter implements UserNamesPort {

    private final QueryUsersPort usuarios;

    public UserNamesModuleAdapter(QueryUsersPort usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public Map<Long, String> nombres(Collection<Long> ids) {
        return usuarios.nombres(ids);
    }

    @Override
    public Set<Long> idsQueCoinciden(String texto) {
        return usuarios.idsQueCoinciden(texto);
    }
}
