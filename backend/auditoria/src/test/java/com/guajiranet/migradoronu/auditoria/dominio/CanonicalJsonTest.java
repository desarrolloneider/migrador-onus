package com.guajiranet.migradoronu.auditoria.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.guajiranet.migradoronu.comun.puertos.ActorType;

class CanonicalJsonTest {

    @Test
    void elMismoContenidoDaElMismoTextoSinImportarElOrdenDeLasClaves() {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("b", 1);
        a.put("a", Map.of("y", true, "x", "texto"));
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("a", Map.of("x", "texto", "y", true));
        b.put("b", 1);

        assertThat(CanonicalJson.write(a)).isEqualTo(CanonicalJson.write(b)).isEqualTo("{\"a\":{\"x\":\"texto\",\"y\":true},\"b\":1}");
    }

    @Test
    void losNumerosQuedanIgualesVengaComoVengaElTipo() {
        assertThat(List.of(1, 1L, new BigDecimal("1.00"), 1.0d, BigInteger.ONE))
                .extracting(CanonicalJson::write).containsOnly("1");
        assertThat(CanonicalJson.write(new BigDecimal("1.50"))).isEqualTo("1.5");
        assertThat(CanonicalJson.write(1.5e10)).isEqualTo("15000000000");
        assertThat(CanonicalJson.write(-0.0d)).isEqualTo("0");
        assertThatThrownBy(() -> CanonicalJson.write(Double.NaN)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void escapaLoQueJsonExigeYDejaLasTildes() {
        assertThat(CanonicalJson.write("ó \"x\" \\ \n\t\u0001")).isEqualTo("\"ó \\\"x\\\" \\\\ \\n\\t\\u0001\"");
    }

    @Test
    void enumeracionesFechasYListasSeEscribenComoTexto() {
        assertThat(CanonicalJson.write(Arrays.asList(ActorType.CONSOLA, Instant.parse("2026-10-05T15:00:00Z"), null)))
                .isEqualTo("[\"CONSOLA\",\"2026-10-05T15:00:00Z\",null]");
    }

    @Test
    void rechazaLoQueNoSePodriaReconstruir() {
        assertThatThrownBy(() -> CanonicalJson.write(Map.of("x", new Object()))).hasMessageContaining("Tipo no admitido");
        Map<Object, Object> repeated = new LinkedHashMap<>();
        repeated.put(1, "a");
        repeated.put("1", "b");
        assertThatThrownBy(() -> CanonicalJson.write(repeated)).hasMessageContaining("Clave repetida");
    }
}
