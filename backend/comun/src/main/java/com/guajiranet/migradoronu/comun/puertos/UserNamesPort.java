package com.guajiranet.migradoronu.comun.puertos;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Puerto transversal: los nombres de los usuarios, para mostrar quién hizo algo. Varias tablas guardan solo el id
 * del usuario; ningún módulo lee la tabla de usuarios, todos preguntan aquí. Lo implementa ensamblaje con acceso.
 */
public interface UserNamesPort {

    /** Nombre de cada id conocido; los que no existen no aparecen en el mapa. */
    Map<Long, String> nombres(Collection<Long> ids);

    /** Ids de los usuarios cuyo usuario o nombre contienen el texto, sin importar mayúsculas. */
    Set<Long> idsQueCoinciden(String texto);
}
