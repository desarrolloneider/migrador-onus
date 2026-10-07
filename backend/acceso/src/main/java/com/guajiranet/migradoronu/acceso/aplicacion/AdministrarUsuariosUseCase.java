package com.guajiranet.migradoronu.acceso.aplicacion;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.guajiranet.migradoronu.acceso.dominio.NombreDeUsuario;
import com.guajiranet.migradoronu.acceso.dominio.PoliticaDeAcceso;
import com.guajiranet.migradoronu.acceso.dominio.Usuario;
import com.guajiranet.migradoronu.acceso.puertos.entrada.ManageUsersPort;
import com.guajiranet.migradoronu.acceso.puertos.salida.PasswordHasherPort;
import com.guajiranet.migradoronu.acceso.puertos.salida.UserRepositoryPort;
import com.guajiranet.migradoronu.comun.errores.ErrorDeNegocio;
import com.guajiranet.migradoronu.comun.puertos.ActorType;
import com.guajiranet.migradoronu.comun.puertos.AuditAction;
import com.guajiranet.migradoronu.comun.puertos.AuditEntry;
import com.guajiranet.migradoronu.comun.puertos.AuditTrailPort;

/**
 * Administración de usuarios por consola (ESP-02). Sin roles en el MVP (DEC-05): crear usuarios desde la pantalla lo
 * podría hacer cualquiera, por eso solo se hace en el servidor (PC-13).
 */
public class AdministrarUsuariosUseCase implements ManageUsersPort {

    private final UserRepositoryPort usuarios;
    private final PasswordHasherPort huellas;
    private final AuditTrailPort auditoria;
    private final Clock reloj;

    public AdministrarUsuariosUseCase(UserRepositoryPort usuarios, PasswordHasherPort huellas, AuditTrailPort auditoria, Clock reloj) {
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
        this.huellas = Objects.requireNonNull(huellas, "huellas");
        this.auditoria = Objects.requireNonNull(auditoria, "auditoria");
        this.reloj = Objects.requireNonNull(reloj, "reloj");
    }

    @Override
    public Usuario crear(String usuario, String nombre, char[] clave) {
        String limpio = NombreDeUsuario.validar(usuario);
        String nombreLimpio = nombre == null ? "" : nombre.strip();
        if (nombreLimpio.isEmpty() || nombreLimpio.length() > 120) {
            throw ErrorDeNegocio.datosInvalidos("El nombre es obligatorio y admite hasta 120 caracteres.");
        }
        exigirClave(clave);
        if (usuarios.buscarPorUsuario(limpio).isPresent()) {
            throw ErrorDeNegocio.conflicto("El usuario " + limpio + " ya existe.");
        }
        Usuario nuevo = Usuario.nuevo(limpio, nombreLimpio, huellas.huella(clave), reloj.instant());
        long id = usuarios.insertar(nuevo);
        registrar(AuditAction.USUARIO_CREADO, id, limpio, "Usuario " + limpio + " (" + nombreLimpio + ") creado por consola.");
        return new Usuario(id, nuevo.usuario(), nuevo.nombre(), nuevo.hashClave(), true, nuevo.creadoEn(), null, 0, null);
    }

    @Override
    public void cambiarClave(String usuario, char[] clave) {
        exigirClave(clave);
        Usuario actual = existente(usuario);
        usuarios.actualizar(actual.conClave(huellas.huella(clave)));
        registrar(AuditAction.CLAVE_CAMBIADA, actual.id(), actual.usuario(), "Contraseña de " + actual.usuario() + " cambiada por consola.");
    }

    @Override
    public void desactivar(String usuario) {
        Usuario actual = existente(usuario);
        if (actual.activo()) {
            usuarios.actualizar(actual.conActivo(false));
            registrar(AuditAction.USUARIO_DESACTIVADO, actual.id(), actual.usuario(),
                    "Usuario " + actual.usuario() + " desactivado por consola. Su sesión abierta se rechaza en su siguiente acción.");
        }
    }

    @Override
    public void reactivar(String usuario) {
        Usuario actual = existente(usuario);
        if (!actual.activo()) {
            usuarios.actualizar(actual.conActivo(true));
            registrar(AuditAction.USUARIO_REACTIVADO, actual.id(), actual.usuario(), "Usuario " + actual.usuario() + " reactivado por consola.");
        }
    }

    @Override
    public List<Usuario> listar() {
        return usuarios.listar();
    }

    private Usuario existente(String usuario) {
        String limpio = NombreDeUsuario.normalizar(usuario);
        return usuarios.buscarPorUsuario(limpio)
                .orElseThrow(() -> ErrorDeNegocio.noEncontrado("El usuario " + limpio + " no existe."));
    }

    private static void exigirClave(char[] clave) {
        if (clave == null || clave.length < PoliticaDeAcceso.LARGO_MINIMO_CLAVE) {
            throw ErrorDeNegocio.datosInvalidos("La contraseña debe tener al menos " + PoliticaDeAcceso.LARGO_MINIMO_CLAVE + " caracteres.");
        }
        boolean soloEspacios = true;
        for (char c : clave) {
            soloEspacios &= Character.isWhitespace(c);
        }
        if (soloEspacios) {
            throw ErrorDeNegocio.datosInvalidos("La contraseña no puede ser solo espacios.");
        }
    }

    private void registrar(AuditAction accion, long usuarioId, String usuario, String mensaje) {
        auditoria.record(AuditEntry.builder(ActorType.CONSOLA, accion)
                .usuarioId(usuarioId)
                .sistema("acceso")
                .peticion(Map.of("usuario", usuario))
                .resultado("OK")
                .mensaje(mensaje)
                .build());
    }

    /** Borra de memoria una contraseña escrita, apenas deja de necesitarse. */
    public static void olvidar(char[] clave) {
        if (clave != null) {
            Arrays.fill(clave, '\0');
        }
    }
}
