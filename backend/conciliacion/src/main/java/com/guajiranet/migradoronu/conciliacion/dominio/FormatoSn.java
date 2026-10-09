package com.guajiranet.migradoronu.conciliacion.dominio;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * El formato común del SN (PC-20): 16 caracteres hexadecimales en mayúscula. SmartOLT puede escribirlo con el
 * fabricante en letras ({@code HWTC1A2B3C4D}) o ya en hexadecimal ({@code 485754431A2B3C4D}); los dos quedan iguales.
 */
public final class FormatoSn {

    private static final Pattern HEX16 = Pattern.compile("[0-9A-F]{16}");
    private static final Pattern FABRICANTE_Y_HEX8 = Pattern.compile("([A-Z0-9]{4})([0-9A-F]{8})");

    private FormatoSn() {
    }

    public static Optional<String> normalizar(String crudo) {
        if (crudo == null) {
            return Optional.empty();
        }
        String limpio = crudo.strip().replace("-", "").replace(" ", "").replace(":", "").toUpperCase(Locale.ROOT);
        if (HEX16.matcher(limpio).matches()) {
            return Optional.of(limpio);
        }
        var m = FABRICANTE_Y_HEX8.matcher(limpio);
        if (m.matches()) {
            return Optional.of(HexFormat.of().withUpperCase().formatHex(m.group(1).getBytes(StandardCharsets.US_ASCII)) + m.group(2));
        }
        return Optional.empty();
    }
}
