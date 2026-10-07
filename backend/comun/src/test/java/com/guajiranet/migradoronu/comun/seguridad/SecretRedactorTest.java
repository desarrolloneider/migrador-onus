package com.guajiranet.migradoronu.comun.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SecretRedactorTest {

    @Test
    void reconoceLasClavesSensiblesSinImportarMayusculasNiGuiones() {
        assertThat(SecretRedactor.isSensitiveKey("Authorization")).isTrue();
        assertThat(SecretRedactor.isSensitiveKey("X-Api-Key")).isTrue();
        assertThat(SecretRedactor.isSensitiveKey("SMARTOLT_API_TOKEN")).isTrue();
        assertThat(SecretRedactor.isSensitiveKey("hash_clave")).isTrue();
        assertThat(SecretRedactor.isSensitiveKey("sn")).isFalse();
        assertThat(SecretRedactor.isSensitiveKey(null)).isFalse();
    }

    @Test
    void ocultaLosValoresSensiblesTambienEnMapasYListasAnidados() {
        Map<String, Object> redacted = SecretRedactor.redact(Map.of(
                "url", "https://smartolt/api/onu/HWTC1A2B3C4D",
                "headers", Map.of("X-Token", "abc123", "Accept", "application/json"),
                "intentos", List.of(Map.of("password", "secreta"), "texto")));

        assertThat(redacted).containsEntry("url", "https://smartolt/api/onu/HWTC1A2B3C4D");
        assertThat(redacted.get("headers")).isEqualTo(Map.of("X-Token", SecretRedactor.MASK, "Accept", "application/json"));
        assertThat(redacted.get("intentos")).isEqualTo(List.of(Map.of("password", SecretRedactor.MASK), "texto"));
    }

    @Test
    void ocultaCredencialesConocidasEnTextoLibre() {
        String respuestaOlt = "User name:migrador\r\nUser password:Cl4ve-OLT\r\nMA5800>";

        String redacted = SecretRedactor.redact(respuestaOlt, List.of("Cl4ve-OLT", "Cl4ve"));

        assertThat(redacted).doesNotContain("Cl4ve").contains(SecretRedactor.MASK).endsWith("MA5800>");
    }

    @Test
    void ocultaCredencialesConocidasDentroDeLosTextosDeUnMapa() {
        Map<String, Object> redacted = SecretRedactor.redact(Map.of(
                "comando", "login migrador Cl4ve-OLT",
                "lineas", List.of("ok", "eco: Cl4ve-OLT"),
                "token", "otro"), List.of("Cl4ve-OLT"));

        assertThat(redacted).containsEntry("comando", "login migrador " + SecretRedactor.MASK)
                .containsEntry("lineas", List.of("ok", "eco: " + SecretRedactor.MASK))
                .containsEntry("token", SecretRedactor.MASK);
    }

    @Test
    void noOmiteCredencialesCortas() {
        assertThat(SecretRedactor.redact("clave=abc", List.of("abc"))).isEqualTo("clave=" + SecretRedactor.MASK);
    }
}
