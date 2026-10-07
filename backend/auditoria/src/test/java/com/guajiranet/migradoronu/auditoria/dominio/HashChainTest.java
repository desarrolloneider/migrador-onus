package com.guajiranet.migradoronu.auditoria.dominio;

import static com.guajiranet.migradoronu.auditoria.dominio.AuditFixtures.T0;
import static com.guajiranet.migradoronu.auditoria.dominio.AuditFixtures.event;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.guajiranet.migradoronu.comun.puertos.ActorType;
import com.guajiranet.migradoronu.comun.puertos.AuditAction;

class HashChainTest {

    @Test
    void laHuellaEsEstableYDependeDeLaHuellaAnterior() {
        String first = HashChain.hashOf(null, event("a"));

        assertThat(first).matches("[0-9a-f]{64}").isEqualTo(HashChain.hashOf(null, event("a")));
        assertThat(HashChain.hashOf(first, event("a"))).isNotEqualTo(first);
    }

    @Test
    void cualquierCambioEnElEventoCambiaLaHuella() {
        String original = HashChain.hashOf(null, event("ok"));

        assertThat(HashChain.hashOf(null, event("ok "))).isNotEqualTo(original);
        assertThat(HashChain.hashOf(null, event(T0.plusNanos(1_000), "ok"))).isNotEqualTo(original);
    }

    @Test
    void losNumerosLeidosDeLaBaseDanLaMismaHuella() {
        AuditEvent written = event("ok");
        Map<String, Object> respuestaLeida = Map.of("texto", "MA5800(config-if-gpon-0/1)#", "duracionMs", new BigDecimal("412"));
        AuditEvent read = new AuditEvent(written.ocurridoEn(), written.actorTipo(), written.usuarioId(), written.ipOrigen(),
                written.accion(), written.loteId(), written.ejecucionId(), written.sn(), written.paso(), written.sistema(),
                written.peticion(), respuestaLeida, written.resultado(), written.codigo(), written.mensaje(), written.duracionMs());

        assertThat(HashChain.hashOf(null, read)).isEqualTo(HashChain.hashOf(null, written));
    }

    @Test
    void elEventoExigeMicrosegundosYRespetaElLargoDeLasColumnas() {
        assertThatThrownBy(() -> event(T0.plusNanos(1), "x")).hasMessageContaining("microsegundos");
        assertThatThrownBy(() -> new AuditEvent(T0, ActorType.CONSOLA, null, null, AuditAction.INTEGRIDAD_VERIFICADA,
                null, null, null, "P010", null, null, null, null, null, null, null))
                .hasMessageContaining("paso");
    }

    @Test
    void elCaracterNuloSeReemplazaPorquePostgresqlNoLoAdmite() {
        AuditEvent withNul = new AuditEvent(T0, ActorType.CONSOLA, null, null, AuditAction.INTEGRIDAD_VERIFICADA,
                null, null, null, null, null, Map.of("texto", "a\u0000b"), null, null, null, "x\u0000y", null);

        assertThat(withNul.mensaje()).isEqualTo("x�y");
        assertThat(withNul.peticion()).containsEntry("texto", "a�b");
    }

    @Test
    void unEventoSelladoNoAceptaHuellasInvalidas() {
        assertThatThrownBy(() -> new SealedAuditEvent(event("x"), "zz", "a".repeat(64))).hasMessageContaining("previousHash");
    }
}
