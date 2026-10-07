package com.guajiranet.migradoronu.auditoria.infraestructura.salida.secretos;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;

import com.guajiranet.migradoronu.auditoria.puertos.salida.KnownSecretsPort;
import com.guajiranet.migradoronu.comun.seguridad.SecretRedactor;

/**
 * Junta al arrancar los valores de toda propiedad o variable de entorno cuyo nombre indica una credencial
 * (SMARTOLT_API_TOKEN, OLT_{CODIGO}_PASSWORD, spring.datasource.password...).
 *
 * <p>No se buscan en el texto libre los valores de menos de 4 caracteres ni {@code true}/{@code false}, para no
 * destrozar los mensajes. Esos valores igual se ocultan cuando aparecen bajo una clave sensible.
 */
public class EnvironmentKnownSecretsAdapter implements KnownSecretsPort {

    static final int MIN_SEARCHABLE_LENGTH = 4;

    private final List<String> secrets;

    public EnvironmentKnownSecretsAdapter(ConfigurableEnvironment environment) {
        Set<String> values = new LinkedHashSet<>();
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (source instanceof EnumerablePropertySource<?> enumerable) {
                for (String name : enumerable.getPropertyNames()) {
                    if (SecretRedactor.isSensitiveKey(name)) {
                        addIfSearchable(values, resolve(environment, name));
                    }
                }
            }
        }
        this.secrets = List.copyOf(values);
    }

    @Override
    public Collection<String> secretValues() {
        return secrets;
    }

    private static String resolve(ConfigurableEnvironment environment, String name) {
        try {
            return environment.getProperty(name);
        } catch (IllegalArgumentException unresolvablePlaceholder) {
            return null;
        }
    }

    private static void addIfSearchable(Set<String> values, String value) {
        if (value == null || value.length() < MIN_SEARCHABLE_LENGTH
                || value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            return;
        }
        values.add(value);
    }
}
