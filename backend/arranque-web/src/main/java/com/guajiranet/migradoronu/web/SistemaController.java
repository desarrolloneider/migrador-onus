package com.guajiranet.migradoronu.web;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.guajiranet.migradoronu.lotes.puertos.entrada.QueryBatchPort;

/**
 * Estado general para la barra superior (arquitectura 9.1). El ejecutor se considera activo si su latido tiene menos
 * de 30 segundos (late cada 10). Lectura directa mientras el módulo ejecucion no tenga sus consultas.
 */
@RestController
public class SistemaController {

    public record LoteActivo(long id, String numero) {
    }

    public record EstadoSistema(boolean ejecutorActivo, Instant ultimoLatido, LoteActivo loteEnEjecucion, long fallidasSinRevisar) {
    }

    private static final Duration LATIDO_VIGENTE = Duration.ofSeconds(30);

    private final JdbcClient jdbc;
    private final QueryBatchPort lotes;

    public SistemaController(DataSource dataSource, QueryBatchPort lotes) {
        this.jdbc = JdbcClient.create(dataSource);
        this.lotes = lotes;
    }

    @GetMapping("/api/v1/system/status")
    public EstadoSistema estado() {
        Optional<Instant> latido = jdbc.sql("SELECT max(ultimo_latido_en) FROM procesos_ejecutores WHERE detenido_en IS NULL")
                .query((rs, n) -> Optional.ofNullable(rs.getObject(1, OffsetDateTime.class)).map(OffsetDateTime::toInstant))
                .single();
        long fallidas = jdbc.sql("SELECT count(*) FROM ejecuciones_onu WHERE estado = 'FALLIDA' AND NOT revisada")
                .query(Long.class).single();
        boolean activo = latido.map(l -> Duration.between(l, Instant.now()).compareTo(LATIDO_VIGENTE) < 0).orElse(false);
        return new EstadoSistema(activo, latido.orElse(null),
                lotes.findRunning().map(l -> new LoteActivo(l.id(), l.numero())).orElse(null), fallidas);
    }
}
