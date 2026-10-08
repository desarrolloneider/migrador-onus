package com.guajiranet.migradoronu.acceso.infraestructura.entrada.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.guajiranet.migradoronu.acceso.dominio.Modulo;
import com.guajiranet.migradoronu.acceso.dominio.Permisos;

class ReglasDePermisoTest {

    private static boolean puede(Permisos p, String metodo, String ruta) {
        return ReglasDePermiso.para(metodo, ruta).map(e -> e.cumple(p)).orElse(true);
    }

    @Test
    void soloLotesPuedeArmarPeroNoEjecutar() {
        Permisos soloLotes = new Permisos(false, Set.of(Modulo.LOTES));
        assertThat(puede(soloLotes, "GET", "/api/v1/batches")).isTrue();
        assertThat(puede(soloLotes, "POST", "/api/v1/batches/preview")).isTrue();
        assertThat(puede(soloLotes, "GET", "/api/v1/olts/OLT-CONEJO/onus")).isTrue();
        assertThat(puede(soloLotes, "POST", "/api/v1/batches/7/confirm")).isFalse();
        assertThat(puede(soloLotes, "POST", "/api/v1/batches/7/stop")).isFalse();
        assertThat(puede(soloLotes, "GET", "/api/v1/audit")).isFalse();
        assertThat(puede(soloLotes, "PUT", "/api/v1/settings/perfil_tr069")).isFalse();
        assertThat(puede(soloLotes, "GET", "/api/v1/users")).isFalse();
    }

    @Test
    void loQueTodosPuedenUsar() {
        Permisos nada = Permisos.ninguno();
        assertThat(puede(nada, "GET", "/api/v1/system/status")).isTrue();
        assertThat(puede(nada, "POST", "/api/v1/auth/cambiar-credenciales")).isTrue();
        assertThat(puede(nada, "GET", "/api/v1/settings")).isTrue();
        assertThat(puede(nada, "GET", "/api/v1/olts")).isTrue();
    }

    @Test
    void elAdministradorPuedeTodo() {
        Permisos admin = new Permisos(true, Set.of());
        assertThat(puede(admin, "GET", "/api/v1/users")).isTrue();
        assertThat(puede(admin, "POST", "/api/v1/batches/7/confirm")).isTrue();
        assertThat(puede(admin, "PUT", "/api/v1/olts/8/boards")).isTrue();
    }
}
