package com.guajiranet.migradoronu.acceso.aplicacion;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import com.guajiranet.migradoronu.acceso.dominio.Modulo;
import com.guajiranet.migradoronu.acceso.dominio.NombreDeUsuario;
import com.guajiranet.migradoronu.acceso.dominio.Permisos;
import com.guajiranet.migradoronu.acceso.dominio.Usuario;
import com.guajiranet.migradoronu.acceso.puertos.salida.PasswordHasherPort;
import com.guajiranet.migradoronu.acceso.puertos.salida.UserRepositoryPort;
import com.guajiranet.migradoronu.comun.errores.ErrorDeNegocio;
import com.guajiranet.migradoronu.comun.puertos.ActorType;
import com.guajiranet.migradoronu.comun.puertos.AuditAction;
import com.guajiranet.migradoronu.comun.puertos.AuditEntry;
import com.guajiranet.migradoronu.comun.puertos.AuditTrailPort;

/**
 * Gestión de usuarios desde la pantalla, solo para administradores: crear, cambiar nombre y módulos, desactivar,
 * reactivar y restablecer la contraseña. Ningún usuario se borra (ESP-02). Todo queda en la auditoría a nombre del
 * administrador que lo hizo.
 *
 * <p>Las contraseñas temporales se generan aquí, se muestran una sola vez y el usuario debe cambiarlas al entrar.
 */
public class GestionUsuariosUseCase {

    /** Un usuario como lo ve el administrador. */
    public record Ficha(long id, String usuario, String nombre, boolean activo, boolean administrador, Set<Modulo> modulos,
                        Instant creadoEn, Instant ultimoIngresoEn, boolean bloqueado, boolean debeCambiarClave) {
    }

    /** El usuario creado o con la contraseña restablecida, con la contraseña temporal que se muestra una sola vez. */
    public record ConClaveTemporal(Ficha ficha, String claveTemporal) {
    }

    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom AZAR = new SecureRandom();

    private final UserRepositoryPort usuarios;
    private final PasswordHasherPort huellas;
    private final AuditTrailPort auditoria;
    private final Clock reloj;

    public GestionUsuariosUseCase(UserRepositoryPort usuarios, PasswordHasherPort huellas, AuditTrailPort auditoria, Clock reloj) {
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
        this.huellas = Objects.requireNonNull(huellas, "huellas");
        this.auditoria = Objects.requireNonNull(auditoria, "auditoria");
        this.reloj = Objects.requireNonNull(reloj, "reloj");
    }

    public List<Ficha> listar() {
        return usuarios.listar().stream().map(this::ficha)
                .sorted(Comparator.comparing(Ficha::activo).reversed().thenComparing(Ficha::usuario)).toList();
    }

    public Ficha ficha(long id) {
        return ficha(existente(id));
    }

    public ConClaveTemporal crear(long actorId, String ip, String usuario, String nombre, boolean administrador, Set<Modulo> modulos) {
        String limpio = NombreDeUsuario.validar(usuario);
        String nombreLimpio = nombreValido(nombre);
        if (usuarios.buscarPorUsuario(limpio).isPresent()) {
            throw ErrorDeNegocio.conflicto("El usuario " + limpio + " ya existe.");
        }
        char[] clave = generarClave();
        try {
            long id = usuarios.insertar(Usuario.nuevo(limpio, nombreLimpio, huellas.huella(clave), reloj.instant()));
            Usuario creado = usuarios.buscarPorId(id).orElseThrow();
            usuarios.cambiarCredenciales(id, limpio, creado.hashClave(), true);
            Permisos permisos = new Permisos(administrador, modulos);
            usuarios.guardarPermisos(id, permisos);
            auditar(AuditAction.USUARIO_CREADO, actorId, ip, id, limpio,
                    "Usuario " + limpio + " (" + nombreLimpio + ") creado con " + describir(permisos) + ".");
            return new ConClaveTemporal(ficha(id), new String(clave));
        } finally {
            Arrays.fill(clave, '\0');
        }
    }

    public Ficha actualizar(long actorId, String ip, long id, String nombre, boolean administrador, Set<Modulo> modulos) {
        Usuario u = existente(id);
        if (id == actorId && !administrador) {
            throw ErrorDeNegocio.datosInvalidos("No te puedes quitar a ti mismo el permiso de administrador.");
        }
        String nombreLimpio = nombreValido(nombre);
        Permisos antes = usuarios.permisos(id);
        Permisos despues = new Permisos(administrador, modulos);
        if (!nombreLimpio.equals(u.nombre())) {
            usuarios.actualizar(new Usuario(u.id(), u.usuario(), nombreLimpio, u.hashClave(), u.activo(), u.creadoEn(), u.ultimoIngresoEn(),
                    u.intentosFallidos(), u.bloqueadoHasta()));
        }
        usuarios.guardarPermisos(id, despues);
        Map<String, Object> peticion = new LinkedHashMap<>();
        peticion.put("usuario", u.usuario());
        peticion.put("antes", describir(antes));
        peticion.put("despues", describir(despues));
        peticion.put("nombre", nombreLimpio);
        auditoria.record(AuditEntry.builder(ActorType.USUARIO, AuditAction.USUARIO_PERMISOS_CAMBIADOS).usuarioId(actorId).ipOrigen(ip)
                .sistema("acceso").peticion(peticion).resultado("OK")
                .mensaje("Permisos de " + u.usuario() + ": " + describir(antes) + " → " + describir(despues) + ".").build());
        return ficha(id);
    }

    public Ficha desactivar(long actorId, String ip, long id) {
        if (id == actorId) {
            throw ErrorDeNegocio.datosInvalidos("No te puedes desactivar a ti mismo.");
        }
        Usuario u = existente(id);
        usuarios.actualizar(u.conActivo(false));
        auditar(AuditAction.USUARIO_DESACTIVADO, actorId, ip, id, u.usuario(),
                "Usuario " + u.usuario() + " desactivado. Su sesión abierta se rechaza en la siguiente acción.");
        return ficha(id);
    }

    public Ficha reactivar(long actorId, String ip, long id) {
        Usuario u = existente(id);
        usuarios.actualizar(u.conActivo(true));
        auditar(AuditAction.USUARIO_REACTIVADO, actorId, ip, id, u.usuario(), "Usuario " + u.usuario() + " reactivado.");
        return ficha(id);
    }

    /** Pone una contraseña temporal, desbloquea y obliga a cambiarla al entrar. */
    public ConClaveTemporal restablecerClave(long actorId, String ip, long id) {
        Usuario u = existente(id);
        char[] clave = generarClave();
        try {
            usuarios.cambiarCredenciales(id, u.usuario(), huellas.huella(clave), true);
            auditar(AuditAction.CLAVE_RESTABLECIDA, actorId, ip, id, u.usuario(),
                    "Contraseña de " + u.usuario() + " restablecida: debe cambiarla al entrar.");
            return new ConClaveTemporal(ficha(id), new String(clave));
        } finally {
            Arrays.fill(clave, '\0');
        }
    }

    private Ficha ficha(Usuario u) {
        Permisos p = usuarios.permisos(u.id());
        return new Ficha(u.id(), u.usuario(), u.nombre(), u.activo(), p.administrador(), new TreeSet<>(p.modulos()), u.creadoEn(),
                u.ultimoIngresoEn(), u.bloqueado(reloj.instant()), usuarios.debeCambiarClave(u.id()));
    }

    private Usuario existente(long id) {
        return usuarios.buscarPorId(id).orElseThrow(() -> ErrorDeNegocio.noEncontrado("No existe ese usuario."));
    }

    private static String nombreValido(String nombre) {
        String limpio = nombre == null ? "" : nombre.strip();
        if (limpio.isEmpty() || limpio.length() > 120) {
            throw ErrorDeNegocio.datosInvalidos("El nombre es obligatorio y admite hasta 120 caracteres.");
        }
        return limpio;
    }

    private static String describir(Permisos p) {
        if (p.administrador()) {
            return "administrador (todos los módulos)";
        }
        return p.modulos().isEmpty() ? "sin módulos" : "módulos " + String.join(", ", p.modulos().stream().map(Modulo::nombre).sorted().toList());
    }

    private void auditar(AuditAction accion, long actorId, String ip, long usuarioAfectado, String usuario, String mensaje) {
        auditoria.record(AuditEntry.builder(ActorType.USUARIO, accion).usuarioId(actorId).ipOrigen(ip).sistema("acceso")
                .peticion(Map.of("usuario", usuario, "usuarioId", usuarioAfectado)).resultado("OK").mensaje(mensaje).build());
    }

    /** 14 caracteres sin letras que se confunden (0/O, 1/l). Cumple el mínimo de 12. */
    private static char[] generarClave() {
        char[] clave = new char[14];
        for (int i = 0; i < clave.length; i++) {
            clave[i] = ALFABETO.charAt(AZAR.nextInt(ALFABETO.length()));
        }
        return clave;
    }
}
