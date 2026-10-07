package com.guajiranet.migradoronu.auditoria.dominio;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.guajiranet.migradoronu.comun.puertos.ActorType;
import com.guajiranet.migradoronu.comun.puertos.AuditAction;

/**
 * Un evento de auditoría tal como se guarda y se encadena (arquitectura 10.1).
 *
 * <p>Todo lo que entra en la huella debe poder reconstruirse exactamente desde la base, por eso:
 * <ul>
 *   <li>la hora va con precisión de microsegundos, la misma de {@code timestamptz};</li>
 *   <li>el carácter nulo se reemplaza, porque PostgreSQL no lo admite en {@code text} ni en {@code jsonb};</li>
 *   <li>los textos respetan el largo de su columna, para que guardar un evento nunca falle por eso.</li>
 * </ul>
 */
public record AuditEvent(
        Instant ocurridoEn,
        ActorType actorTipo,
        Long usuarioId,
        String ipOrigen,
        AuditAction accion,
        Long loteId,
        Long ejecucionId,
        String sn,
        String paso,
        String sistema,
        Map<String, Object> peticion,
        Map<String, Object> respuesta,
        String resultado,
        String codigo,
        String mensaje,
        Long duracionMs) {

    /** Versión de la forma canónica. Si algún día cambia, los eventos viejos se verifican con la suya. */
    public static final String FORMAT = "auditoria-v1";

    private static final char NUL = '\u0000';
    private static final char REPLACEMENT = '�';

    public AuditEvent {
        Objects.requireNonNull(ocurridoEn, "ocurridoEn");
        Objects.requireNonNull(actorTipo, "actorTipo");
        Objects.requireNonNull(accion, "accion");
        if (ocurridoEn.getNano() % 1_000 != 0) {
            throw new IllegalArgumentException("La hora de un evento va con precisión de microsegundos: " + ocurridoEn);
        }
        if (duracionMs != null && duracionMs < 0) {
            throw new IllegalArgumentException("La duración no puede ser negativa: " + duracionMs);
        }
        ipOrigen = column(ipOrigen, 45, "ipOrigen");
        sn = column(sn, 64, "sn");
        paso = column(paso, 3, "paso");
        sistema = column(sistema, 60, "sistema");
        resultado = column(resultado, 20, "resultado");
        codigo = column(codigo, 40, "codigo");
        mensaje = sanitize(mensaje);
        peticion = sanitizeMap(peticion);
        respuesta = sanitizeMap(respuesta);
    }

    /** Lo que entra en la huella, con nombres fijos. Los valores ausentes van como {@code null}. */
    public Map<String, Object> canonicalForm() {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("formato", FORMAT);
        form.put("ocurridoEn", DateTimeFormatter.ISO_INSTANT.format(ocurridoEn));
        form.put("actorTipo", actorTipo.name());
        form.put("usuarioId", usuarioId);
        form.put("ipOrigen", ipOrigen);
        form.put("accion", accion.name());
        form.put("loteId", loteId);
        form.put("ejecucionId", ejecucionId);
        form.put("sn", sn);
        form.put("paso", paso);
        form.put("sistema", sistema);
        form.put("peticion", peticion);
        form.put("respuesta", respuesta);
        form.put("resultado", resultado);
        form.put("codigo", codigo);
        form.put("mensaje", mensaje);
        form.put("duracionMs", duracionMs);
        return form;
    }

    private static String column(String value, int maxLength, String field) {
        String clean = sanitize(value);
        if (clean != null && clean.length() > maxLength) {
            throw new IllegalArgumentException("El campo " + field + " de la auditoría admite " + maxLength
                    + " caracteres y trae " + clean.length());
        }
        return clean;
    }

    private static String sanitize(String value) {
        return value == null ? null : value.replace(NUL, REPLACEMENT);
    }

    private static Map<String, Object> sanitizeMap(Map<?, ?> values) {
        if (values == null) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        values.forEach((key, value) -> copy.put(sanitize(String.valueOf(key)), sanitizeValue(value)));
        return Collections.unmodifiableMap(copy);
    }

    private static Object sanitizeValue(Object value) {
        if (value instanceof String text) {
            return sanitize(text);
        }
        if (value instanceof Map<?, ?> map) {
            return sanitizeMap(map);
        }
        if (value instanceof Collection<?> collection) {
            return Collections.unmodifiableList(collection.stream().map(AuditEvent::sanitizeValue).toList());
        }
        if (value instanceof Object[] array) {
            return sanitizeValue(Arrays.asList(array));
        }
        return value;
    }
}
