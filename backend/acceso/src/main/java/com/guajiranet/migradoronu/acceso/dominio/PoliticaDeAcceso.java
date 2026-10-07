package com.guajiranet.migradoronu.acceso.dominio;

import java.time.Duration;
import java.util.Objects;

/**
 * Reglas de acceso de la spec 7.5. Salen de los parámetros de configuración (RNF-11): intentos que bloquean, duración
 * del bloqueo y cierre de sesión por inactividad. El largo mínimo de la contraseña es fijo (ESP-02).
 */
public record PoliticaDeAcceso(int intentosParaBloquear, Duration duracionBloqueo, Duration inactividadDeSesion) {

    public static final int LARGO_MINIMO_CLAVE = 12;

    public PoliticaDeAcceso {
        if (intentosParaBloquear < 1) {
            throw new IllegalArgumentException("Los intentos para bloquear deben ser al menos 1");
        }
        Objects.requireNonNull(duracionBloqueo, "duracionBloqueo");
        Objects.requireNonNull(inactividadDeSesion, "inactividadDeSesion");
    }

    /** Los valores propuestos en la spec 7.5: 5 intentos, 15 minutos de bloqueo, 30 minutos de inactividad. */
    public static PoliticaDeAcceso estandar() {
        return new PoliticaDeAcceso(5, Duration.ofMinutes(15), Duration.ofMinutes(30));
    }
}
