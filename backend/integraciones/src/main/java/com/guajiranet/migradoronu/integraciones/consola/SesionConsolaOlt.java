package com.guajiranet.migradoronu.integraciones.consola;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.regex.Pattern;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.config.keys.KeyUtils;

import com.guajiranet.migradoronu.integraciones.http.ErrorDeIntegracion;

/**
 * Una sesión SSH con la consola de una OLT Huawei (PC-35). Manda un comando y devuelve el texto que respondió la OLT
 * hasta el siguiente prompt ({@code NOMBRE>} o {@code NOMBRE#}, con o sin modo). Pasa sola las páginas
 * {@code ---- More ----} y responde con Enter las preguntas de parámetros opcionales {@code { <cr>|... }:}.
 * No interpreta nada: no sabe qué es un perfil TR-069.
 */
public final class SesionConsolaOlt implements AutoCloseable {

    public static final String SISTEMA = "Consola OLT";

    private static final Pattern PROMPT = Pattern.compile("(?m)^[\\w.\\-]+(\\([\\w\\-/ ]+\\))?[>#]\\s*$");
    private static final Pattern MAS = Pattern.compile("-+\\s*More\\s*\\(.*?\\)\\s*-+");
    private static final Pattern PREGUNTA_OPCIONES = Pattern.compile("\\{\\s*<cr>[^}]*\\}:\\s*$");
    /**
     * "Are you sure to ...? (y/n)[n]:": solo se contesta que sí a {@code ont reset} de una ONU (el reinicio al final del paso
     * 3, decisión del 16 de octubre); a cualquier otra pregunta, que no (nada se confirma a ciegas).
     */
    private static final Pattern PREGUNTA_SI_NO = Pattern.compile("\\(y/n\\)\\s*\\[[yn]\\]\\s*:\\s*$");
    /** {@code ont reset <puerto> <ONT>}: una sola ONU, dentro de la tarjeta. */
    private static final Pattern REINICIO_DE_UNA_ONU = Pattern.compile("^ont reset \\d+ \\d+$");

    static boolean esReinicio(String comando) {
        return comando != null && REINICIO_DE_UNA_ONU.matcher(comando.strip()).matches();
    }
    private static final Pattern ANSI = Pattern.compile("\u001B\\[[0-9;?]*[A-Za-z]|\u001B\\[\\d*D|[\b\r]");

    /** Tiempo sin datos después del prompt para dar por terminado un comando. */
    private static final Duration SILENCIO = Duration.ofMillis(600);

    private final SshClient cliente;
    private final ClientSession sesion;
    private final ChannelShell canal;
    private final OutputStream entrada;
    private final InputStream salida;
    private final Duration tiempoRespuesta;
    private final String huellaServidor;
    private String ultimoPrompt = "";

    private SesionConsolaOlt(SshClient cliente, ClientSession sesion, ChannelShell canal, Duration tiempoRespuesta, String huella) {
        this.cliente = cliente;
        this.sesion = sesion;
        this.canal = canal;
        this.entrada = canal.getInvertedIn();
        this.salida = canal.getInvertedOut();
        this.tiempoRespuesta = tiempoRespuesta;
        this.huellaServidor = huella;
    }

    /**
     * Abre la sesión y espera el primer prompt. La huella de la llave del servidor queda disponible en
     * {@link #huellaServidor()} para compararla (no se valida contra una lista todavía: ver docs/hallazgos).
     */
    public static SesionConsolaOlt abrir(String host, int puerto, String usuario, String clave, Duration tiempoConexion,
                                         Duration tiempoRespuesta) {
        return abrir(host, puerto, usuario, clave, null, tiempoConexion, tiempoRespuesta);
    }

    /**
     * Igual, pero si {@code huellaEsperada} no es nula y la OLT presenta otra llave, no se conecta: puede ser otro equipo
     * haciéndose pasar por la OLT.
     */
    public static SesionConsolaOlt abrir(String host, int puerto, String usuario, String clave, String huellaEsperada,
                                         Duration tiempoConexion, Duration tiempoRespuesta) {
        Objects.requireNonNull(host, "host");
        if (usuario == null || usuario.isBlank() || clave == null || clave.isBlank()) {
            throw new ErrorDeIntegracion(SISTEMA, "No están configurados el usuario y la contraseña de consola de " + host + ".", null);
        }
        SshClient cliente = SshClient.setUpDefaultClient();
        permitirAlgoritmosAntiguos(cliente);
        String[] huella = new String[1];
        cliente.setServerKeyVerifier((sesion, direccion, llave) -> {
            huella[0] = huella(llave);
            return huellaEsperada == null || huellaEsperada.isBlank() || huellaEsperada.strip().equals(huella[0]);
        });
        cliente.start();
        try {
            ClientSession sesion = cliente.connect(usuario, host, puerto).verify(tiempoConexion).getSession();
            sesion.addPasswordIdentity(clave);
            sesion.auth().verify(tiempoConexion);
            ChannelShell canal = sesion.createShellChannel();
            canal.setPtyType("vt100");
            canal.setPtyColumns(512);
            canal.setPtyLines(500);
            canal.open().verify(tiempoConexion);
            SesionConsolaOlt consola = new SesionConsolaOlt(cliente, sesion, canal, tiempoRespuesta, huella[0]);
            consola.leerHastaPrompt();
            return consola;
        } catch (IOException e) {
            cliente.stop();
            if (huella[0] != null && huellaEsperada != null && !huellaEsperada.isBlank() && !huellaEsperada.strip().equals(huella[0])) {
                throw new ErrorDeIntegracion(SISTEMA, host + " presentó una llave SSH distinta de la registrada (" + huella[0]
                        + "). No se conectó: puede ser otro equipo. Verifica con el encargado de la OLT.", e);
            }
            String mensaje = e.getMessage() != null && e.getMessage().toLowerCase().contains("auth")
                    ? host + " rechazó el usuario o la contraseña de consola."
                    : "No se pudo conectar a " + host + ":" + puerto + " por SSH (" + e.getMessage() + ").";
            throw new ErrorDeIntegracion(SISTEMA, mensaje, e);
        } catch (RuntimeException e) {
            cliente.stop();
            throw e;
        }
    }

    /** El prompt en que quedó la OLT, por ejemplo {@code OLT-CONEJO(config-if-gpon-0/0)#}. */
    public String prompt() {
        return ultimoPrompt;
    }

    public String huellaServidor() {
        return huellaServidor;
    }

    /** Manda un comando y devuelve lo que respondió la OLT, sin el eco del comando ni el prompt final. */
    public String ejecutar(String comando) {
        try {
            entrada.write((comando + "\n").getBytes(StandardCharsets.US_ASCII));
            entrada.flush();
        } catch (IOException e) {
            throw new ErrorDeIntegracion(SISTEMA, "Se perdió la sesión con la OLT al mandar el comando.", e);
        }
        String texto = leerHastaPrompt(comando);
        String[] lineas = texto.split("\n", -1);
        // La respuesta empieza después del último eco del comando: si un aviso de la OLT interrumpe el eco, la OLT lo
        // vuelve a escribir con el prompt delante, y lo que había antes (restos de avisos) no es parte de la respuesta.
        int inicio = 0;
        for (int i = 0; i < lineas.length - 1; i++) {
            if (lineas[i].strip().endsWith(comando.strip())) {
                inicio = i + 1;
            }
        }
        StringBuilder limpio = new StringBuilder();
        for (int i = inicio; i < lineas.length - 1; i++) {
            limpio.append(lineas[i]).append('\n');
        }
        return limpio.toString().strip();
    }

    /** El primer prompt al abrir la sesión: no hay eco que esperar. */
    private String leerHastaPrompt() {
        return leerHastaPrompt(null);
    }

    /**
     * Lee hasta que la OLT termina de responder {@code comando}: ya se vio su eco, la última línea es un prompt y la
     * OLT se quedó callada {@link #SILENCIO}. Mientras guarda, la OLT escribe avisos y vuelve a dibujar el prompt; ese
     * prompt no cuenta como fin del comando (si no, las respuestas quedan corridas un comando).
     */
    private String leerHastaPrompt(String comando) {
        ByteArrayOutputStream recibido = new ByteArrayOutputStream();
        byte[] bufer = new byte[8192];
        Instant limite = Instant.now().plus(tiempoRespuesta);
        boolean ecoVisto = comando == null;
        Instant promptVistoEn = null;
        try {
            while (Instant.now().isBefore(limite)) {
                if (salida.available() > 0) {
                    int n = salida.read(bufer);
                    if (n < 0) {
                        throw new ErrorDeIntegracion(SISTEMA, "La OLT cerró la sesión.", null);
                    }
                    recibido.write(bufer, 0, n);
                    // Diagnóstico solo desde la consola (java -Dconsola.crudo=true ... perfiles-crudo <OLT>): lo que manda la OLT tal cual.
                    if (Boolean.getBoolean("consola.crudo")) {
                        System.out.println("<<CRUDO>>" + new String(bufer, 0, n, StandardCharsets.UTF_8)
                                .replace("\u001B", "<ESC>").replace("\r", "<CR>").replace("\n", "<LF>\n").replace("\b", "<BS>"));
                    }
                    promptVistoEn = null;
                    String texto = limpiar(recibido.toString(StandardCharsets.UTF_8));
                    if (!ecoVisto) {
                        ecoVisto = contieneEco(texto, comando);
                    }
                    if (MAS.matcher(ultimaLinea(texto)).find()) {
                        recibido.reset();
                        recibido.write(MAS.matcher(texto).replaceAll("").getBytes(StandardCharsets.UTF_8));
                        enviar(" ");
                        continue;
                    }
                    if (PREGUNTA_SI_NO.matcher(texto).find()) {
                        recibido.reset();
                        recibido.write(PREGUNTA_SI_NO.matcher(texto).replaceAll("").getBytes(StandardCharsets.UTF_8));
                        enviar(esReinicio(comando) ? "y\n" : "n\n");
                        continue;
                    }
                    if (PREGUNTA_OPCIONES.matcher(texto).find()) {
                        recibido.reset();
                        recibido.write(PREGUNTA_OPCIONES.matcher(texto).replaceAll("").getBytes(StandardCharsets.UTF_8));
                        enviar("\n");
                        continue;
                    }
                    if (ecoVisto && PROMPT.matcher(ultimaLineaUtil(texto)).matches()) {
                        promptVistoEn = Instant.now();
                    }
                    limite = Instant.now().plus(tiempoRespuesta);
                } else {
                    if (promptVistoEn != null && Instant.now().isAfter(promptVistoEn.plus(SILENCIO))) {
                        String texto = limpiar(recibido.toString(StandardCharsets.UTF_8));
                        ultimoPrompt = ultimaLineaUtil(texto).strip();
                        return texto;
                    }
                    if (canal.isClosed()) {
                        throw new ErrorDeIntegracion(SISTEMA, "La OLT cerró la sesión.", null);
                    }
                    canal.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), 50);
                }
            }
        } catch (IOException e) {
            throw new ErrorDeIntegracion(SISTEMA, "Se perdió la sesión con la OLT.", e);
        }
        throw new ErrorDeIntegracion(SISTEMA, "La OLT no respondió en " + tiempoRespuesta.toSeconds() + " segundos. Última salida: "
                + resumen(limpiar(recibido.toString(StandardCharsets.UTF_8))), null);
    }

    private static boolean contieneEco(String texto, String comando) {
        for (String linea : texto.split("\n")) {
            if (linea.strip().endsWith(comando.strip())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sigue leyendo lo que la OLT escribe sola (sin mandar nada) hasta ver {@code patron} o hasta {@code maximo}. Sirve
     * para esperar a que termine de guardar: la OLT avisa "saved completely" cuando acaba. Devuelve si lo vio.
     */
    public boolean esperarTexto(Pattern patron, Duration maximo) {
        StringBuilder visto = new StringBuilder();
        byte[] bufer = new byte[8192];
        Instant limite = Instant.now().plus(maximo);
        try {
            while (Instant.now().isBefore(limite)) {
                if (salida.available() > 0) {
                    int n = salida.read(bufer);
                    if (n < 0) {
                        return false;
                    }
                    visto.append(limpiar(new String(bufer, 0, n, StandardCharsets.UTF_8)));
                    if (patron.matcher(visto).find()) {
                        return true;
                    }
                } else {
                    if (canal.isClosed()) {
                        return false;
                    }
                    canal.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), 100);
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    private void enviar(String texto) throws IOException {
        entrada.write(texto.getBytes(StandardCharsets.US_ASCII));
        entrada.flush();
    }

    /**
     * Aplica lo que haría una terminal: retroceso borra el carácter anterior, un retorno de carro solo (sin salto de
     * línea) vuelve al inicio de la línea, y "cursor a la izquierda" ({@code ESC[nD}) borra n caracteres. La OLT usa
     * esto para redibujar el comando cuando un aviso lo interrumpe; si solo se quitaran, el eco quedaría deformado
     * ({@code tr069-sver-config}) y la herramienta no reconocería su propio comando.
     */
    static String limpiar(String texto) {
        StringBuilder r = new StringBuilder(texto.length());
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c == '\u001B' && i + 1 < texto.length() && texto.charAt(i + 1) == '[') {
                int j = i + 2;
                while (j < texto.length() && (Character.isDigit(texto.charAt(j)) || texto.charAt(j) == ';' || texto.charAt(j) == '?')) {
                    j++;
                }
                if (j < texto.length()) {
                    if (texto.charAt(j) == 'D') {
                        String n = texto.substring(i + 2, j);
                        borrar(r, n.isEmpty() ? 1 : Integer.parseInt(n.replaceAll("\\D", "").isEmpty() ? "1" : n.replaceAll("\\D", "")));
                    }
                    i = j;
                } else {
                    i = j - 1;
                }
            } else if (c == '\b') {
                borrar(r, 1);
            } else if (c == '\r') {
                if (i + 1 < texto.length() && texto.charAt(i + 1) == '\n') {
                    continue;
                }
                r.setLength(r.lastIndexOf("\n") + 1);
            } else {
                r.append(c);
            }
        }
        return r.toString();
    }

    /** Borra hasta n caracteres de la línea actual, sin pasar a la anterior. */
    private static void borrar(StringBuilder r, int n) {
        for (int k = 0; k < n && r.length() > 0 && r.charAt(r.length() - 1) != '\n'; k++) {
            r.setLength(r.length() - 1);
        }
    }

    /** Un aviso asíncrono de la OLT, por ejemplo "1 [2026-10-07 16:18:53-05:00]:The percentage of saved data ...: 84%". */
    private static final Pattern AVISO = Pattern.compile("^\\s*\\d+\\s+\\[\\d{4}-\\d{2}-\\d{2}[^\\]]*\\]:.*$");

    /**
     * La última línea que cuenta para saber si la OLT terminó: se saltan las vacías y los avisos asíncronos, porque la
     * OLT puede escribir un aviso después del prompt sin volver a dibujarlo.
     */
    static String ultimaLineaUtil(String texto) {
        String[] lineas = texto.split("\n", -1);
        for (int i = lineas.length - 1; i >= 0; i--) {
            String l = lineas[i];
            if (l.isBlank() && i == lineas.length - 1) {
                continue;
            }
            if (AVISO.matcher(l).matches() || (l.isBlank() && i < lineas.length - 1)) {
                continue;
            }
            return l;
        }
        return "";
    }

    private static String ultimaLinea(String texto) {
        int i = texto.lastIndexOf('\n');
        return i < 0 ? texto : texto.substring(i + 1);
    }

    private static String resumen(String texto) {
        String t = texto.strip();
        return t.length() > 200 ? "…" + t.substring(t.length() - 200) : t;
    }

    /**
     * Algunas OLT Huawei viejas solo negocian diffie-hellman con SHA-1 y cifrados CBC. Se agregan al final de la lista:
     * con una OLT moderna se sigue usando lo moderno. La huella de la llave se sigue verificando igual.
     */
    private static void permitirAlgoritmosAntiguos(SshClient cliente) {
        var kex = new java.util.ArrayList<>(cliente.getKeyExchangeFactories());
        for (var dh : java.util.List.of(org.apache.sshd.common.kex.BuiltinDHFactories.dhgex,
                org.apache.sshd.common.kex.BuiltinDHFactories.dhg14, org.apache.sshd.common.kex.BuiltinDHFactories.dhg1)) {
            if (kex.stream().noneMatch(k -> k.getName().equals(dh.getName()))) {
                kex.add(org.apache.sshd.client.ClientBuilder.DH2KEX.apply(dh));
            }
        }
        cliente.setKeyExchangeFactories(kex);
        var cifrados = new java.util.ArrayList<>(cliente.getCipherFactories());
        for (var c : java.util.List.of(org.apache.sshd.common.cipher.BuiltinCiphers.aes128cbc,
                org.apache.sshd.common.cipher.BuiltinCiphers.aes256cbc, org.apache.sshd.common.cipher.BuiltinCiphers.tripledescbc)) {
            if (cifrados.stream().noneMatch(x -> x.getName().equals(c.getName()))) {
                cifrados.add(c);
            }
        }
        cliente.setCipherFactories(cifrados);
        var macs = new java.util.ArrayList<>(cliente.getMacFactories());
        if (macs.stream().noneMatch(m -> m.getName().equals(org.apache.sshd.common.mac.BuiltinMacs.hmacsha1.getName()))) {
            macs.add(org.apache.sshd.common.mac.BuiltinMacs.hmacsha1);
        }
        cliente.setMacFactories(macs);
        var firmas = new java.util.ArrayList<>(cliente.getSignatureFactories());
        if (firmas.stream().noneMatch(x -> x.getName().equals(org.apache.sshd.common.signature.BuiltinSignatures.rsa.getName()))) {
            firmas.add(org.apache.sshd.common.signature.BuiltinSignatures.rsa);
        }
        cliente.setSignatureFactories(firmas);
    }

    private static String huella(PublicKey llave) {
        return KeyUtils.getFingerPrint(llave);
    }

    @Override
    public void close() {
        try {
            enviar("quit\n");
        } catch (IOException | RuntimeException ignorada) {
            // La sesión ya estaba cerrada.
        }
        canal.close(true);
        sesion.close(true);
        cliente.stop();
    }
}
