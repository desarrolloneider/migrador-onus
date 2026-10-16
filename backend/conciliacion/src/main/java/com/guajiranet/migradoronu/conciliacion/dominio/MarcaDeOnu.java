package com.guajiranet.migradoronu.conciliacion.dominio;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * La marca de una ONU a partir de lo único que trae SmartOLT: el SN y el modelo (onu_type_name). SmartOLT no da la marca,
 * y ninguno de los dos datos basta solo (inventario del 15 de octubre de 2026):
 * <ul>
 *   <li>Hay equipos genéricos con SN "HWTC" que no son Huawei (MONUV411, E4L-H5400WA, FD511GW…).</li>
 *   <li>Hay modelos Huawei puestos a mano sobre SN de otro fabricante (HG8545M con SN DC90).</li>
 *   <li>Unas 170 HWTC tienen un modelo genérico ("ONU-type-eth-…") y no se sabe qué equipo son.</li>
 * </ul>
 * Por eso es "Huawei" solo cuando el SN es HWTC <b>y</b> el modelo es de la familia Huawei (EG8…, HG8…, HS8…). Es la
 * única marca con procedimiento: las demás se muestran con un nombre que no se confunde con ella.
 */
public final class MarcaDeOnu {

    public static final String HUAWEI = "Huawei";

    /** Código de fabricante de los 4 primeros caracteres del SN. Los que no están se muestran como "Genérica (código)". */
    static final Map<String, String> FABRICANTES = Map.of(
            "HWTC", HUAWEI, "ZTEG", "ZTE", "FHTT", "FiberHome", "TPLG", "TP-Link", "OPTI", "Optictimes",
            "RUIJ", "Ruijie", "HNET", "Hikvision", "GPON", "Genérica GPON");

    /** Familias de modelo → marca, en orden. La primera que coincide gana. */
    private record Familia(Pattern modelo, String marca) {
    }

    private static final Pattern MODELO_HUAWEI = Pattern.compile("^(EG8|HG8|HS8)[0-9A-Z]");
    private static final List<Familia> FAMILIAS = List.of(
            new Familia(Pattern.compile("^F6\\d\\d"), "ZTE"),
            new Familia(Pattern.compile("^XC2\\d\\d"), "TP-Link"),
            new Familia(Pattern.compile("^HG6\\d{3}"), "FiberHome"),
            new Familia(Pattern.compile("^MONU"), "Genérica MONU"),
            new Familia(Pattern.compile("^E4L-"), "Genérica E4L"),
            new Familia(Pattern.compile("^(FD\\d|D5\\d\\d)"), "C-Data"),
            new Familia(Pattern.compile("^ZX8"), "Genérica ZX"));
    private static final Pattern MODELO_SIN_DEFINIR = Pattern.compile("^ONU-TYPE-");

    private MarcaDeOnu() {
    }

    /** La marca para mostrar y filtrar. Solo {@link #HUAWEI} tiene procedimiento. */
    public static String de(String sn, String modelo) {
        String prefijo = prefijo(sn);
        String porSn = prefijo == null ? "Desconocida" : FABRICANTES.getOrDefault(prefijo, "Genérica (" + prefijo + ")");
        String m = modelo == null ? "" : modelo.strip().toUpperCase(Locale.ROOT);
        if (m.isEmpty() || MODELO_SIN_DEFINIR.matcher(m).find()) {
            return porSn + " (modelo sin definir)";
        }
        if (MODELO_HUAWEI.matcher(m).find()) {
            return "HWTC".equals(prefijo) ? HUAWEI : "No confirmada (" + modelo.strip() + " con SN " + prefijo + ")";
        }
        for (Familia f : FAMILIAS) {
            if (f.modelo().matcher(m).find()) {
                return f.marca();
            }
        }
        // Modelo que no está en ninguna familia: se dice tal cual, para que no se confunda con la marca del SN.
        return porSn.equals(HUAWEI) ? "No confirmada (" + modelo.strip() + " con SN HWTC)" : porSn;
    }

    /** Los 4 primeros caracteres del SN, también si viene en hexadecimal (4857544347ADC1B3 → HWTC). */
    static String prefijo(String sn) {
        if (sn == null || sn.strip().length() < 4) {
            return null;
        }
        String s = sn.strip().toUpperCase(Locale.ROOT);
        if (s.matches("[0-9A-F]{16}")) {
            String ascii = new String(HexFormat.of().parseHex(s.substring(0, 8)), StandardCharsets.US_ASCII);
            if (ascii.matches("[A-Z0-9]{4}")) {
                return ascii;
            }
        }
        return s.substring(0, 4);
    }
}
