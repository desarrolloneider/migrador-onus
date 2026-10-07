package com.guajiranet.migradoronu.acceso.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.guajiranet.migradoronu.acceso.dominio.NombreDeUsuario;
import com.guajiranet.migradoronu.acceso.dominio.PoliticaDeAcceso;
import com.guajiranet.migradoronu.acceso.dominio.ResultadoAutenticacion;
import com.guajiranet.migradoronu.acceso.dominio.ResultadoAutenticacion.Motivo;
import com.guajiranet.migradoronu.acceso.dominio.Usuario;
import com.guajiranet.migradoronu.acceso.puertos.entrada.AuthenticatePort;
import com.guajiranet.migradoronu.acceso.puertos.salida.AccessPolicyPort;
import com.guajiranet.migradoronu.acceso.puertos.salida.PasswordHasherPort;
import com.guajiranet.migradoronu.acceso.puertos.salida.UserRepositoryPort;
import com.guajiranet.migradoronu.comun.puertos.ActorType;
import com.guajiranet.migradoronu.comun.puertos.AuditAction;
import com.guajiranet.migradoronu.comun.puertos.AuditEntry;
import com.guajiranet.migradoronu.comun.puertos.AuditTrailPort;

/**
 * Inicio de sesión (ESP-01). El usuario inexistente, la contraseña incorrecta y el usuario desactivado dan el mismo
 * resultado, y en los tres casos se calcula una huella, para que el tiempo de respuesta no delate cuál fue.
 */
public class AutenticarUseCase implements AuthenticatePort {

    private static final char[] CLAVE_FICTICIA = "clave-ficticia-para-igualar-tiempos".toCharArray();

    private final UserRepositoryPort usuarios;
    private final PasswordHasherPort huellas;
    private final AccessPolicyPort politicas;
    private final AuditTrailPort auditoria;
    private final Clock reloj;
    private final String huellaFicticia;

    public AutenticarUseCase(UserRepositoryPort usuarios, PasswordHasherPort huellas, AccessPolicyPort politicas,
                             AuditTrailPort auditoria, Clock reloj) {
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
        this.huellas = Objects.requireNonNull(huellas, "huellas");
        this.politicas = Objects.requireNonNull(politicas, "politicas");
        this.auditoria = Objects.requireNonNull(auditoria, "auditoria");
        this.reloj = Objects.requireNonNull(reloj, "reloj");
        this.huellaFicticia = huellas.huella(CLAVE_FICTICIA);
    }

    @Override
    public ResultadoAutenticacion autenticar(String usuario, String clave, String ipOrigen) {
        Instant ahora = reloj.instant();
        PoliticaDeAcceso politica = politicas.politica();
        String nombre = NombreDeUsuario.normalizar(usuario);
        char[] claveEscrita = clave == null ? new char[0] : clave.toCharArray();
        Optional<Usuario> encontrado = nombre.isEmpty() ? Optional.empty() : usuarios.buscarPorUsuario(nombre);

        if (encontrado.isEmpty()) {
            huellas.coincide(claveEscrita, huellaFicticia);
            registrar(AuditAction.SESION_FALLIDA, null, ipOrigen, nombre, "RECHAZADA", "Usuario o contraseña incorrectos.");
            return new ResultadoAutenticacion.Rechazada(Motivo.CREDENCIALES, 0);
        }
        Usuario actual = encontrado.get();
        if (actual.bloqueado(ahora)) {
            registrar(AuditAction.SESION_FALLIDA, actual.id(), ipOrigen, nombre, "BLOQUEADO",
                    "Usuario bloqueado por intentos fallidos.");
            return new ResultadoAutenticacion.Rechazada(Motivo.BLOQUEADO, actual.minutosDeBloqueo(ahora));
        }
        boolean claveCorrecta = huellas.coincide(claveEscrita, actual.hashClave());
        if (!actual.activo()) {
            registrar(AuditAction.SESION_FALLIDA, actual.id(), ipOrigen, nombre, "RECHAZADA", "El usuario está desactivado.");
            return new ResultadoAutenticacion.Rechazada(Motivo.CREDENCIALES, 0);
        }
        if (!claveCorrecta) {
            Usuario conFallo = actual.conIntentoFallido(ahora, politica);
            usuarios.actualizar(conFallo);
            registrar(AuditAction.SESION_FALLIDA, actual.id(), ipOrigen, nombre, "RECHAZADA", "Usuario o contraseña incorrectos.");
            if (conFallo.bloqueado(ahora)) {
                registrar(AuditAction.USUARIO_BLOQUEADO, actual.id(), ipOrigen, nombre, "BLOQUEADO",
                        "Usuario bloqueado " + politica.duracionBloqueo().toMinutes() + " minutos tras "
                                + politica.intentosParaBloquear() + " intentos fallidos seguidos.");
                return new ResultadoAutenticacion.Rechazada(Motivo.BLOQUEADO, conFallo.minutosDeBloqueo(ahora));
            }
            return new ResultadoAutenticacion.Rechazada(Motivo.CREDENCIALES, 0);
        }
        Usuario conIngreso = actual.conIngreso(ahora);
        usuarios.actualizar(conIngreso);
        registrar(AuditAction.SESION_INICIADA, actual.id(), ipOrigen, nombre, "OK", "Sesión iniciada.");
        return new ResultadoAutenticacion.Exitosa(conIngreso, politica.inactividadDeSesion());
    }

    @Override
    public void registrarCierre(long usuarioId, String ipOrigen) {
        auditoria.record(AuditEntry.builder(ActorType.USUARIO, AuditAction.SESION_CERRADA)
                .usuarioId(usuarioId).ipOrigen(ipOrigen).resultado("OK").mensaje("Sesión cerrada por el usuario.").build());
    }

    private void registrar(AuditAction accion, Long usuarioId, String ip, String usuarioEscrito, String resultado, String mensaje) {
        auditoria.record(AuditEntry.builder(ActorType.USUARIO, accion)
                .usuarioId(usuarioId)
                .ipOrigen(ip)
                .sistema("acceso")
                .peticion(Map.of("usuario", usuarioEscrito))
                .resultado(resultado)
                .mensaje(mensaje)
                .build());
    }
}
