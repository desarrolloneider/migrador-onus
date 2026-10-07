package com.guajiranet.migradoronu.comun.seguridad;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Quita credenciales de lo que se va a guardar en la auditoría o en los registros técnicos (RN-25, RNF-06,
 * arquitectura 10.3).
 */
public final class SecretRedactor {

    public static final String MASK = "[REDACTADO]";

    private static final List<String> SENSITIVE_KEY_PARTS =
            List.of("token", "password", "secret", "authorization", "api_key", "apikey", "clave");

    private SecretRedactor() {
    }

    /** Una clave es sensible si su nombre contiene token, password, secret, authorization, api_key o clave. */
    public static boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT).replace('-', '_');
        return SENSITIVE_KEY_PARTS.stream().anyMatch(normalized::contains);
    }

    /** Copia del mapa con el valor de toda clave sensible reemplazado, también en mapas y listas anidados. */
    public static Map<String, Object> redact(Map<?, ?> values) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (values == null) {
            return result;
        }
        values.forEach((key, value) -> {
            String name = String.valueOf(key);
            result.put(name, isSensitiveKey(name) ? MASK : redactValue(value));
        });
        return result;
    }

    /**
     * Como {@link #redact(Map)} y además reemplaza cada credencial conocida dentro de todos los textos del mapa,
     * también en mapas y listas anidados.
     */
    public static Map<String, Object> redact(Map<?, ?> values, Collection<String> knownSecrets) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (values == null) {
            return result;
        }
        values.forEach((key, value) -> {
            String name = String.valueOf(key);
            result.put(name, isSensitiveKey(name) ? MASK : redactValue(value, knownSecrets));
        });
        return result;
    }

    /**
     * Reemplaza en un texto libre (por ejemplo, la respuesta de la consola de la OLT) cada credencial conocida.
     * Se reemplazan primero las más largas para que una credencial contenida en otra no deje restos.
     */
    public static String redact(String text, Collection<String> knownSecrets) {
        if (text == null || knownSecrets == null) {
            return text;
        }
        List<String> secrets = new ArrayList<>();
        for (String secret : knownSecrets) {
            if (secret != null && !secret.isEmpty()) {
                secrets.add(secret);
            }
        }
        secrets.sort(Comparator.comparingInt(String::length).reversed());
        String result = text;
        for (String secret : secrets) {
            result = result.replace(secret, MASK);
        }
        return result;
    }

    private static Object redactValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return redact(map);
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(SecretRedactor::redactValue).toList();
        }
        return value;
    }

    private static Object redactValue(Object value, Collection<String> knownSecrets) {
        if (value instanceof Map<?, ?> map) {
            return redact(map, knownSecrets);
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(item -> redactValue(item, knownSecrets)).toList();
        }
        if (value instanceof String text) {
            return redact(text, knownSecrets);
        }
        return value;
    }
}
