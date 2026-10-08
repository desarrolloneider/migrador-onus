package com.guajiranet.migradoronu.procedimientos.dominio;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lo que interesa de {@code display ont info <F> <S> <P> <ONT>} en una OLT Huawei (confirmado con OLT-CONEJO el 7 de
 * octubre de 2026): el SN, si está en línea y el perfil TR-069. La descripción no se lee: lleva datos del cliente.
 *
 * @param perfilId     {@code TR069 server profile ID}; vacío si la ONU no tiene perfil
 * @param perfilNombre {@code TR069 server profile name}
 */
public record LecturaOntHuawei(String sn, boolean enLinea, Optional<Integer> perfilId, Optional<String> perfilNombre) {

    private static final Pattern SN = Pattern.compile("(?m)^\\s*SN\\s*:\\s*([0-9A-Fa-f]{16})");
    private static final Pattern ESTADO = Pattern.compile("(?m)^\\s*Run state\\s*:\\s*(\\S+)");
    private static final Pattern PERFIL_ID = Pattern.compile("(?m)^\\s*TR069 server profile ID\\s*:\\s*(\\S+)");
    private static final Pattern PERFIL_NOMBRE = Pattern.compile("(?m)^\\s*TR069 server profile name\\s*:\\s*(\\S+)");

    /** Vacío si la OLT respondió un error o falta el SN o el estado: no se adivina (RN-13). */
    public static Optional<LecturaOntHuawei> leer(String texto) {
        if (texto == null || RespuestaHuawei.esError(texto)) {
            return Optional.empty();
        }
        texto = RespuestaHuawei.sinAvisos(texto);
        Matcher sn = SN.matcher(texto);
        Matcher estado = ESTADO.matcher(texto);
        if (!sn.find() || !estado.find()) {
            return Optional.empty();
        }
        Optional<Integer> id = buscar(PERFIL_ID, texto).filter(v -> v.matches("\\d+")).map(Integer::valueOf);
        Optional<String> nombre = buscar(PERFIL_NOMBRE, texto).filter(v -> !v.equals("-"));
        return Optional.of(new LecturaOntHuawei(sn.group(1).toUpperCase(), estado.group(1).equalsIgnoreCase("online"), id, nombre));
    }

    private static Optional<String> buscar(Pattern patron, String texto) {
        Matcher m = patron.matcher(texto);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }
}
