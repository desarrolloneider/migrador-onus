package com.guajiranet.migradoronu.acceso.dominio;

import java.time.Duration;
import java.util.Objects;

/** Lo que pasó al intentar entrar (ESP-01). */
public sealed interface ResultadoAutenticacion {

    /** Entró. La sesión vence tras {@code inactividad} sin uso. */
    record Exitosa(Usuario usuario, Duration inactividad) implements ResultadoAutenticacion {
        public Exitosa {
            Objects.requireNonNull(usuario, "usuario");
            Objects.requireNonNull(inactividad, "inactividad");
        }
    }

    /** No entró. Con usuario inexistente, contraseña incorrecta o usuario desactivado el motivo es el mismo. */
    record Rechazada(Motivo motivo, long minutosDeBloqueo) implements ResultadoAutenticacion {
        public Rechazada {
            Objects.requireNonNull(motivo, "motivo");
        }
    }

    enum Motivo { CREDENCIALES, BLOQUEADO }
}
