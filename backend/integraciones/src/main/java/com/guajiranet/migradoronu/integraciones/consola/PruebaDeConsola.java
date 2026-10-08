package com.guajiranet.migradoronu.integraciones.consola;

import java.io.PrintStream;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.guajiranet.migradoronu.integraciones.http.ErrorDeIntegracion;

/**
 * Prueba de solo lectura de la consola de una OLT: entra por SSH y ejecuta únicamente comandos {@code display}.
 * No entra a modo de configuración ni cambia nada. Muestra lo que respondió la OLT para ajustar el procedimiento.
 */
public final class PruebaDeConsola {

    /** Solo lectura. Nunca agregar aquí un comando que cambie algo. */
    static final List<String> COMANDOS = List.of(
            "enable",
            "display ont tr069-server-profile all",
            "display ont tr069-server-profile bound-info profile-id 2",
            "display ont tr069-server-profile bound-info profile-id 3");

    /** Una línea {@code Campo : valor} que empieza con el campo. */
    private static final Pattern CAMPO = Pattern.compile("^\\s*[A-Z][\\w ()/\\-]*?\\s+:\\s?.*$");
    /** Un campo {@code Campo : valor} al final de una línea, después de varios espacios. */
    private static final Pattern CAMPO_PEGADO = Pattern.compile("\\s{2,}([A-Z][\\w ()/\\-]*?\\s+:\\s?.*)$");

    private final PrintStream salida;

    /**
     * La descripción de la ONU lleva el nombre del cliente y puede ocupar varias líneas: se oculta completa, hasta la
     * siguiente línea con forma {@code Campo : valor}.
     */
    static String sinDatosDeCliente(String texto) {
        StringBuilder salida = new StringBuilder();
        boolean ocultando = false;
        for (String linea : texto.split("\n", -1)) {
            if (linea.matches("(?i)\\s*Description\\s*:.*")) {
                salida.append(linea, 0, linea.indexOf(':') + 1).append(" (oculta)\n");
                ocultando = true;
                continue;
            }
            if (ocultando) {
                if (CAMPO.matcher(linea).matches() || linea.matches("\\s*-{5,}.*")) {
                    ocultando = false;
                } else {
                    // La OLT a veces pega el resto de la descripción con el campo siguiente en la misma línea.
                    Matcher pegado = CAMPO_PEGADO.matcher(linea);
                    if (pegado.find()) {
                        salida.append("  ").append(pegado.group(1)).append('\n');
                        ocultando = false;
                    }
                    continue;
                }
            }
            salida.append(linea).append('\n');
        }
        return salida.toString().stripTrailing();
    }

    public PruebaDeConsola(PrintStream salida) {
        this.salida = salida;
    }

    /**
     * @param huella     la huella SSH registrada de la OLT; si es nula se conecta igual y avisa
     * @param puertoOnu  opcional, por ejemplo {@code 0/0/4}: con {@code ontId} lee esa ONU con display ont info
     */
    public int ejecutar(String olt, String host, int puerto, String usuario, String clave, String huella, String puertoOnu,
                        Integer ontId) {
        salida.println("== " + olt + " (" + host + ":" + puerto + ", SSH)");
        try (SesionConsolaOlt consola = SesionConsolaOlt.abrir(host, puerto, usuario, clave, huella, Duration.ofSeconds(10),
                Duration.ofSeconds(30))) {
            salida.println("Sesión abierta. Huella de la llave del servidor: " + consola.huellaServidor()
                    + (huella == null || huella.isBlank() ? "  (AVISO: no hay huella registrada; no se verificó)" : "  (verificada)"));
            for (String comando : COMANDOS) {
                salida.println();
                salida.println(">>> " + comando);
                salida.println(consola.ejecutar(comando));
            }
            if (puertoOnu != null && ontId != null) {
                String[] fsp = puertoOnu.split("/");
                String comando = "display ont info " + fsp[0] + " " + fsp[1] + " " + fsp[2] + " " + ontId;
                salida.println();
                salida.println(">>> " + comando);
                salida.println(sinDatosDeCliente(consola.ejecutar(comando)));
            }
            salida.println();
            salida.println("Prueba terminada: no se cambió nada en la OLT.");
            return 0;
        } catch (ErrorDeIntegracion e) {
            salida.println("ERROR: " + e.getMessage());
            return 1;
        }
    }
}
