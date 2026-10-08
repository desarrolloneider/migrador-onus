package com.guajiranet.migradoronu.procedimientos.dominio;

import java.util.regex.Pattern;

/**
 * Reglas para leer la respuesta de una OLT Huawei (confirmadas con OLT-CONEJO el 7 de octubre de 2026).
 *
 * <ul>
 *   <li>La OLT intercala avisos asíncronos en cualquier respuesta, por ejemplo mientras guarda:
 *       {@code 1 [2026-10-07 14:57:47-05:00]:The percentage of saved data on 2 slot's control board is: 78%}.
 *       No son parte de la respuesta y se quitan antes de leerla.</li>
 *   <li>Un error de la OLT es una línea que empieza con {@code %} ({@code % Unknown command...},
 *       {@code % Parameter error...}) o que dice {@code Failure:} o {@code Error:}. Un porcentaje suelto no es error.</li>
 *   <li>{@code Failure: System is busy, please retry after a while} es pasajero: la OLT está guardando. Se reintenta.</li>
 * </ul>
 */
public final class RespuestaHuawei {

    private static final Pattern AVISO_ASINCRONO = Pattern.compile("(?m)^\\s*\\d+\\s+\\[\\d{4}-\\d{2}-\\d{2}[^\\]]*\\]:.*(\\R|$)");
    private static final Pattern ERROR = Pattern.compile("(?mi)^\\s*%\\s|\\bFailure\\s*:|\\bError\\s*:");
    private static final Pattern OCUPADA = Pattern.compile("(?i)system is busy");

    private RespuestaHuawei() {
    }

    /** La respuesta sin los avisos asíncronos que la OLT intercala. */
    public static String sinAvisos(String texto) {
        return texto == null ? "" : AVISO_ASINCRONO.matcher(texto).replaceAll("");
    }

    public static boolean esError(String texto) {
        return ERROR.matcher(sinAvisos(texto)).find();
    }

    /** La OLT está ocupada (por ejemplo, guardando la configuración): se puede reintentar más tarde. */
    public static boolean estaOcupada(String texto) {
        return OCUPADA.matcher(sinAvisos(texto)).find();
    }
}
