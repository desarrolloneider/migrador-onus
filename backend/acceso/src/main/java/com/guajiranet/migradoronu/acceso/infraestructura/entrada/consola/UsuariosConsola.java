package com.guajiranet.migradoronu.acceso.infraestructura.entrada.consola;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import com.guajiranet.migradoronu.acceso.aplicacion.AdministrarUsuariosUseCase;
import com.guajiranet.migradoronu.acceso.dominio.Usuario;
import com.guajiranet.migradoronu.acceso.puertos.entrada.ManageUsersPort;
import com.guajiranet.migradoronu.comun.errores.ErrorDeNegocio;

/**
 * Administración de usuarios por consola en el servidor (ESP-02, PC-13):
 * <pre>
 *   java -jar migrador-web.jar usuarios crear &lt;usuario&gt; &lt;nombre completo&gt; [--generar-clave]
 *   java -jar migrador-web.jar usuarios cambiar-clave &lt;usuario&gt; [--generar-clave]
 *   java -jar migrador-web.jar usuarios desactivar &lt;usuario&gt;
 *   java -jar migrador-web.jar usuarios reactivar &lt;usuario&gt;
 *   java -jar migrador-web.jar usuarios listar
 * </pre>
 * La contraseña se pide sin mostrarla y dos veces. Con {@code --generar-clave} se genera una al azar y se muestra una
 * sola vez en la consola; nunca queda en la auditoría ni en los registros.
 */
public final class UsuariosConsola {

    /** De dónde se lee una contraseña. Permite probar sin una terminal real. */
    @FunctionalInterface
    public interface Entrada {
        char[] leerClave(String pregunta);
    }

    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom AZAR = new SecureRandom();

    private final ManageUsersPort usuarios;
    private final PrintStream salida;
    private final Entrada entrada;

    public UsuariosConsola(ManageUsersPort usuarios, PrintStream salida, Entrada entrada) {
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
        this.salida = Objects.requireNonNull(salida, "salida");
        this.entrada = Objects.requireNonNull(entrada, "entrada");
    }

    /** Ejecuta un comando y devuelve el código de salida: 0 bien, 1 error, 2 uso incorrecto. */
    public int ejecutar(String... argumentos) {
        List<String> args = Arrays.stream(argumentos).filter(a -> !a.equals("--generar-clave")).toList();
        boolean generar = argumentos.length != args.size();
        if (args.isEmpty()) {
            return ayuda();
        }
        try {
            switch (args.getFirst()) {
                case "crear" -> {
                    if (args.size() < 3) return ayuda();
                    String nombre = String.join(" ", args.subList(2, args.size()));
                    char[] clave = generar ? generarClave() : pedirClave();
                    try {
                        Usuario creado = usuarios.crear(args.get(1), nombre, clave);
                        salida.println("Usuario " + creado.usuario() + " (" + creado.nombre() + ") creado.");
                        mostrarSiGenerada(generar, clave);
                    } finally {
                        AdministrarUsuariosUseCase.olvidar(clave);
                    }
                }
                case "cambiar-clave" -> {
                    if (args.size() != 2) return ayuda();
                    char[] clave = generar ? generarClave() : pedirClave();
                    try {
                        usuarios.cambiarClave(args.get(1), clave);
                        salida.println("Contraseña de " + args.get(1) + " cambiada. Si estaba bloqueado, quedó desbloqueado.");
                        mostrarSiGenerada(generar, clave);
                    } finally {
                        AdministrarUsuariosUseCase.olvidar(clave);
                    }
                }
                case "desactivar" -> {
                    if (args.size() != 2) return ayuda();
                    usuarios.desactivar(args.get(1));
                    salida.println("Usuario " + args.get(1) + " desactivado. Si tenía sesión abierta, se le niega su siguiente acción.");
                }
                case "reactivar" -> {
                    if (args.size() != 2) return ayuda();
                    usuarios.reactivar(args.get(1));
                    salida.println("Usuario " + args.get(1) + " reactivado.");
                }
                case "listar" -> listar();
                default -> {
                    return ayuda();
                }
            }
            return 0;
        } catch (ErrorDeNegocio error) {
            salida.println("No se hizo: " + error.getMessage());
            return 1;
        }
    }

    private void listar() {
        List<Usuario> todos = usuarios.listar();
        if (todos.isEmpty()) {
            salida.println("No hay usuarios. Crea el primero con: usuarios crear <usuario> <nombre completo>");
            return;
        }
        salida.printf("%-20s %-30s %-12s %s%n", "USUARIO", "NOMBRE", "ESTADO", "ÚLTIMO INGRESO (UTC)");
        for (Usuario u : todos) {
            salida.printf("%-20s %-30s %-12s %s%n", u.usuario(), u.nombre(), u.activo() ? "activo" : "desactivado",
                    u.ultimoIngresoEn() == null ? "nunca" : u.ultimoIngresoEn().toString());
        }
    }

    private char[] pedirClave() {
        char[] primera = entrada.leerClave("Contraseña (mínimo 12 caracteres): ");
        char[] segunda = entrada.leerClave("Repítela: ");
        try {
            if (primera == null || !Arrays.equals(primera, segunda)) {
                AdministrarUsuariosUseCase.olvidar(primera);
                throw ErrorDeNegocio.datosInvalidos("Las contraseñas no coinciden.");
            }
            return primera;
        } finally {
            AdministrarUsuariosUseCase.olvidar(segunda);
        }
    }

    private void mostrarSiGenerada(boolean generada, char[] clave) {
        if (generada) {
            salida.println();
            salida.println("Contraseña generada (se muestra una sola vez, guárdala ahora): " + new String(clave));
            salida.println("Para cambiarla: usuarios cambiar-clave <usuario>");
        }
    }

    /** Cuatro grupos de cuatro caracteres sin letras ni números que se confundan (0/O, 1/l/I). */
    static char[] generarClave() {
        char[] clave = new char[19];
        for (int i = 0; i < clave.length; i++) {
            clave[i] = (i + 1) % 5 == 0 ? '-' : ALFABETO.charAt(AZAR.nextInt(ALFABETO.length()));
        }
        return clave;
    }

    private int ayuda() {
        salida.println("""
                Uso: usuarios <comando>
                  crear <usuario> <nombre completo> [--generar-clave]
                  cambiar-clave <usuario> [--generar-clave]
                  desactivar <usuario>
                  reactivar <usuario>
                  listar""");
        return 2;
    }

    /** Lee de la terminal sin mostrar lo que se escribe; sin terminal (por ejemplo, con una tubería) lee una línea. */
    public static Entrada entradaDelSistema() {
        Console consola = System.console();
        if (consola != null) {
            return consola::readPassword;
        }
        BufferedReader lector = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        return pregunta -> {
            try {
                String linea = lector.readLine();
                return linea == null ? null : linea.toCharArray();
            } catch (IOException e) {
                throw ErrorDeNegocio.datosInvalidos("No se pudo leer la contraseña.");
            }
        };
    }
}
