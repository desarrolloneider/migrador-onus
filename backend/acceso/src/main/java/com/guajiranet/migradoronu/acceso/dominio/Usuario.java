package com.guajiranet.migradoronu.acceso.dominio;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Un usuario de la herramienta (ESP-01, ESP-02). Ningún usuario se borra: se desactiva. La contraseña solo existe
 * como huella Argon2 (RNF-07).
 */
public record Usuario(Long id, String usuario, String nombre, String hashClave, boolean activo, Instant creadoEn,
                      Instant ultimoIngresoEn, int intentosFallidos, Instant bloqueadoHasta) {

    public Usuario {
        Objects.requireNonNull(usuario, "usuario");
        Objects.requireNonNull(nombre, "nombre");
        Objects.requireNonNull(hashClave, "hashClave");
        Objects.requireNonNull(creadoEn, "creadoEn");
        if (intentosFallidos < 0) {
            throw new IllegalArgumentException("Los intentos fallidos no pueden ser negativos");
        }
    }

    public static Usuario nuevo(String usuario, String nombre, String hashClave, Instant ahora) {
        return new Usuario(null, usuario, nombre, hashClave, true, ahora, null, 0, null);
    }

    public boolean bloqueado(Instant ahora) {
        return bloqueadoHasta != null && ahora.isBefore(bloqueadoHasta);
    }

    /** Minutos que faltan para que termine el bloqueo, redondeados hacia arriba (mínimo 1). */
    public long minutosDeBloqueo(Instant ahora) {
        if (!bloqueado(ahora)) {
            return 0;
        }
        long segundos = Duration.between(ahora, bloqueadoHasta).toSeconds();
        return Math.max(1, (segundos + 59) / 60);
    }

    /** Un intento fallido más. Al llegar al límite, el usuario queda bloqueado y el contador vuelve a cero. */
    public Usuario conIntentoFallido(Instant ahora, PoliticaDeAcceso politica) {
        int intentos = intentosFallidos + 1;
        if (intentos >= politica.intentosParaBloquear()) {
            return new Usuario(id, usuario, nombre, hashClave, activo, creadoEn, ultimoIngresoEn, 0, ahora.plus(politica.duracionBloqueo()));
        }
        return new Usuario(id, usuario, nombre, hashClave, activo, creadoEn, ultimoIngresoEn, intentos, bloqueadoHasta);
    }

    public Usuario conIngreso(Instant ahora) {
        return new Usuario(id, usuario, nombre, hashClave, activo, creadoEn, ahora, 0, null);
    }

    /** Cambiar la contraseña también levanta un bloqueo: lo hace el administrador por consola. */
    public Usuario conClave(String nuevoHash) {
        return new Usuario(id, usuario, nombre, nuevoHash, activo, creadoEn, ultimoIngresoEn, 0, null);
    }

    public Usuario conActivo(boolean nuevoActivo) {
        return new Usuario(id, usuario, nombre, hashClave, nuevoActivo, creadoEn, ultimoIngresoEn, intentosFallidos, bloqueadoHasta);
    }
}
