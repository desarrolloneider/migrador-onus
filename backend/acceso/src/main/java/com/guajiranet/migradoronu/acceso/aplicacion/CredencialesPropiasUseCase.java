package com.guajiranet.migradoronu.acceso.aplicacion;

import java.time.Clock;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.guajiranet.migradoronu.acceso.dominio.NombreDeUsuario;
import com.guajiranet.migradoronu.acceso.dominio.Usuario;
import com.guajiranet.migradoronu.acceso.puertos.entrada.OwnCredentialsPort;
import com.guajiranet.migradoronu.acceso.puertos.salida.PasswordHasherPort;
import com.guajiranet.migradoronu.acceso.puertos.salida.UserRepositoryPort;
import com.guajiranet.migradoronu.comun.errores.ErrorDeNegocio;
import com.guajiranet.migradoronu.comun.puertos.ActorType;
import com.guajiranet.migradoronu.comun.puertos.AuditAction;
import com.guajiranet.migradoronu.comun.puertos.AuditEntry;
import com.guajiranet.migradoronu.comun.puertos.AuditTrailPort;

/** El usuario cambia su propia contraseña (y, si quiere, su nombre de usuario) desde la pantalla. */
public class CredencialesPropiasUseCase implements OwnCredentialsPort {

    private final UserRepositoryPort usuarios;
    private final PasswordHasherPort huellas;
    private final AuditTrailPort auditoria;
    private final Clock reloj;

    public CredencialesPropiasUseCase(UserRepositoryPort usuarios, PasswordHasherPort huellas, AuditTrailPort auditoria, Clock reloj) {
        this.usuarios = Objects.requireNonNull(usuarios, "usuarios");
        this.huellas = Objects.requireNonNull(huellas, "huellas");
        this.auditoria = Objects.requireNonNull(auditoria, "auditoria");
        this.reloj = Objects.requireNonNull(reloj, "reloj");
    }

    @Override
    public boolean debeCambiarClave(long usuarioId) {
        return usuarios.debeCambiarClave(usuarioId);
    }

    @Override
    public Usuario cambiar(long usuarioId, String usuarioNuevo, char[] claveActual, char[] claveNueva, String ipOrigen) {
        try {
            Usuario actual = usuarios.buscarPorId(usuarioId)
                    .orElseThrow(() -> new ErrorDeNegocio(ErrorDeNegocio.Tipo.SIN_SESION, "Sin sesión."));
            if (claveActual == null || !huellas.coincide(claveActual, actual.hashClave())) {
                throw ErrorDeNegocio.datosInvalidos("La contraseña actual no es correcta.");
            }
            AdministrarUsuariosUseCase.exigirClave(claveNueva);
            if (Arrays.equals(claveActual, claveNueva)) {
                throw ErrorDeNegocio.datosInvalidos("La contraseña nueva debe ser distinta de la actual.");
            }
            String nombre = usuarioNuevo == null || usuarioNuevo.isBlank() ? actual.usuario() : NombreDeUsuario.validar(usuarioNuevo);
            usuarios.cambiarCredenciales(usuarioId, nombre, huellas.huella(claveNueva), false);
            Map<String, Object> peticion = new LinkedHashMap<>();
            peticion.put("usuarioAnterior", actual.usuario());
            peticion.put("usuarioNuevo", nombre);
            auditoria.record(AuditEntry.builder(ActorType.USUARIO, AuditAction.CLAVE_CAMBIADA)
                    .usuarioId(usuarioId).ipOrigen(ipOrigen).sistema("acceso").peticion(peticion).resultado("OK")
                    .mensaje(nombre.equals(actual.usuario())
                            ? actual.usuario() + " cambió su contraseña."
                            : actual.usuario() + " cambió su contraseña y su usuario a " + nombre + ".")
                    .build());
            return usuarios.buscarPorId(usuarioId).orElseThrow();
        } finally {
            borrar(claveActual);
            borrar(claveNueva);
        }
    }

    @Override
    public void prepararUsuarioInicial(String usuario, char[] clave) {
        try {
            String nombre = NombreDeUsuario.validar(usuario);
            AdministrarUsuariosUseCase.exigirClave(clave);
            var existente = usuarios.buscarPorUsuario(nombre);
            if (existente.isEmpty()) {
                long id = usuarios.insertar(Usuario.nuevo(nombre, "Administrador", huellas.huella(clave), reloj.instant()));
                usuarios.cambiarCredenciales(id, nombre, usuarios.buscarPorId(id).orElseThrow().hashClave(), true);
                registrar(id, nombre, "Usuario inicial " + nombre + " creado desde el .env; debe cambiar la contraseña al entrar.");
            } else if (existente.get().ultimoIngresoEn() == null) {
                Usuario u = existente.get();
                usuarios.cambiarCredenciales(u.id(), nombre, huellas.huella(clave), true);
                registrar(u.id(), nombre, "Contraseña inicial de " + nombre + " puesta desde el .env; debe cambiarla al entrar.");
            }
        } finally {
            borrar(clave);
        }
    }

    private void registrar(long id, String usuario, String mensaje) {
        auditoria.record(AuditEntry.builder(ActorType.CONSOLA, AuditAction.CLAVE_CAMBIADA)
                .usuarioId(id).sistema("acceso").peticion(Map.of("usuario", usuario)).resultado("OK").mensaje(mensaje).build());
    }

    private static void borrar(char[] clave) {
        if (clave != null) {
            Arrays.fill(clave, '\0');
        }
    }
}
