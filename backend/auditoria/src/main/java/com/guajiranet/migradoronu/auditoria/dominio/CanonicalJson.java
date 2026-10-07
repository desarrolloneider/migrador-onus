package com.guajiranet.migradoronu.auditoria.dominio;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.temporal.TemporalAccessor;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * JSON canónico de la herramienta: siempre el mismo texto para el mismo contenido. Es la base de la huella de cada
 * evento y también el texto que se guarda en las columnas {@code jsonb}.
 *
 * <p>Es propio a propósito: si dependiera del formato de una librería, una actualización de esa librería podría
 * cambiar el texto y romper la verificación de toda la cadena.
 *
 * <ul>
 *   <li>Las claves de los objetos se ordenan.</li>
 *   <li>Los números se escriben en su forma decimal más corta: {@code 1}, {@code 1.0} y {@code 1.00} son {@code 1}.
 *       Así da lo mismo que al leer de la base vuelvan como Integer, Long o BigDecimal.</li>
 *   <li>Enumeraciones, fechas y UUID se escriben como texto.</li>
 *   <li>Cualquier otro tipo se rechaza: no se guarda nada que no se pueda reconstruir.</li>
 * </ul>
 */
public final class CanonicalJson {

    private CanonicalJson() {
    }

    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        append(out, value);
        return out.toString();
    }

    private static void append(StringBuilder out, Object value) {
        switch (value) {
            case null -> out.append("null");
            case String text -> appendString(out, text);
            case Boolean bool -> out.append(bool);
            case Number number -> out.append(number(number));
            case Map<?, ?> map -> appendObject(out, map);
            case Collection<?> collection -> appendArray(out, collection);
            case Object[] array -> appendArray(out, java.util.Arrays.asList(array));
            case Enum<?> constant -> appendString(out, constant.name());
            case CharSequence text -> appendString(out, text.toString());
            case TemporalAccessor temporal -> appendString(out, temporal.toString());
            case UUID uuid -> appendString(out, uuid.toString());
            default -> throw new IllegalArgumentException(
                    "Tipo no admitido en la auditoría: " + value.getClass().getName());
        }
    }

    private static void appendObject(StringBuilder out, Map<?, ?> map) {
        TreeMap<String, Object> sorted = new TreeMap<>();
        map.forEach((key, value) -> {
            String name = String.valueOf(key);
            if (sorted.containsKey(name)) {
                throw new IllegalArgumentException("Clave repetida en un objeto de la auditoría: " + name);
            }
            sorted.put(name, value);
        });
        out.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> entry : sorted.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            appendString(out, entry.getKey());
            out.append(':');
            append(out, entry.getValue());
        }
        out.append('}');
    }

    private static void appendArray(StringBuilder out, Collection<?> values) {
        out.append('[');
        boolean first = true;
        for (Object value : values) {
            if (!first) {
                out.append(',');
            }
            first = false;
            append(out, value);
        }
        out.append(']');
    }

    static String number(Number number) {
        if ((number instanceof Double d && (d.isNaN() || d.isInfinite()))
                || (number instanceof Float f && (f.isNaN() || f.isInfinite()))) {
            throw new IllegalArgumentException("Número no representable en JSON: " + number);
        }
        BigDecimal decimal = switch (number) {
            case BigDecimal big -> big;
            case BigInteger big -> new BigDecimal(big);
            default -> new BigDecimal(number.toString());
        };
        return decimal.stripTrailingZeros().toPlainString();
    }

    private static void appendString(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
