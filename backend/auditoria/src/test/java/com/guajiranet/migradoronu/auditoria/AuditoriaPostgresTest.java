package com.guajiranet.migradoronu.auditoria;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.guajiranet.migradoronu.auditoria.aplicacion.AuditPage;
import com.guajiranet.migradoronu.auditoria.aplicacion.AuditSearchCriteria;
import com.guajiranet.migradoronu.auditoria.aplicacion.VerifyAuditIntegrityCommand;
import com.guajiranet.migradoronu.auditoria.dominio.AuditAnchor;
import com.guajiranet.migradoronu.auditoria.dominio.IntegrityReport;
import com.guajiranet.migradoronu.auditoria.infraestructura.configuracion.AuditoriaConfig;
import com.guajiranet.migradoronu.auditoria.infraestructura.salida.archivo.FileAuditAnchorAdapter;
import com.guajiranet.migradoronu.auditoria.puertos.entrada.AnchorAuditPort;
import com.guajiranet.migradoronu.auditoria.puertos.entrada.QueryAuditPort;
import com.guajiranet.migradoronu.auditoria.puertos.entrada.RecordAuditEventPort;
import com.guajiranet.migradoronu.auditoria.puertos.entrada.VerifyAuditIntegrityPort;
import com.guajiranet.migradoronu.comun.infraestructura.ClockConfig;
import com.guajiranet.migradoronu.comun.puertos.ActorType;
import com.guajiranet.migradoronu.comun.puertos.AuditAction;
import com.guajiranet.migradoronu.comun.puertos.AuditEntry;

/**
 * El módulo completo contra PostgreSQL 17 real (arquitectura 13.1). Las alteraciones se hacen como las haría alguien
 * con acceso directo a la base: saltándose los disparadores con {@code session_replication_role = replica}.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = AuditoriaPostgresTest.Aplicacion.class, properties = {
        "spring.liquibase.change-log=classpath:db/changelog/solo-auditoria.yaml",
        "OLT_RIOHACHA01_PASSWORD=Sup3r-Secreta-OLT"
})
class AuditoriaPostgresTest {

    private static final Path ANCHORS_DIR = createTempDir();

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({AuditoriaConfig.class, ClockConfig.class})
    static class Aplicacion {
    }

    @DynamicPropertySource
    static void anchors(DynamicPropertyRegistry registry) {
        registry.add("migrador.auditoria.anclas.directorio", ANCHORS_DIR::toString);
    }

    @Autowired private RecordAuditEventPort record;
    @Autowired private VerifyAuditIntegrityPort verify;
    @Autowired private AnchorAuditPort anchor;
    @Autowired private QueryAuditPort query;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void emptyAuditAndAnchors() throws IOException {
        asIntruder("TRUNCATE auditoria_eventos RESTART IDENTITY");
        Files.deleteIfExists(ANCHORS_DIR.resolve(FileAuditAnchorAdapter.FILE_NAME));
    }

    @Test
    void losEventosQuedanEncadenadosYLaCadenaVerificaIntegra() {
        record.record(step("P04", "interface gpon 0/1"));
        record.record(step("P05", "undo ont tr069-server-config 0 33"));

        List<Map<String, Object>> rows = jdbc.queryForList("SELECT hash_anterior, hash FROM auditoria_eventos ORDER BY id");
        assertThat(rows.get(0).get("hash_anterior")).isNull();
        assertThat(rows.get(1).get("hash_anterior")).isEqualTo(rows.get(0).get("hash"));

        IntegrityReport report = verify.verify(VerifyAuditIntegrityCommand.fromConsole());
        assertThat(report.intact()).as(report.message()).isTrue();
        assertThat(report.checkedEvents()).isEqualTo(2);
    }

    @Test
    void detectaUnEventoModificadoDirectamenteEnLaBase() {
        record.record(step("P04", "interface gpon 0/1"));
        record.record(step("P05", "undo ont tr069-server-config 0 33"));
        record.record(step("P06", "ont tr069-server-config 0 33 profile-name guajiranet1"));

        asIntruder("UPDATE auditoria_eventos SET mensaje = 'todo bien' WHERE id = 2");

        IntegrityReport report = verify.verify(VerifyAuditIntegrityCommand.fromConsole());
        assertThat(report.intact()).isFalse();
        assertThat(report.brokenEventId()).isEqualTo(2);
        assertThat(lastAction()).isEqualTo("INTEGRIDAD_ROTA");
    }

    @Test
    void conAnclaDetectaQueSeBorraronLosUltimosEventos() {
        record.record(step("P04", "interface gpon 0/1"));
        record.record(step("P05", "undo ont tr069-server-config 0 33"));
        AuditAnchor written = anchor.anchorNow(AuditAnchor.BATCH_FINISHED).orElseThrow();

        asIntruder("DELETE FROM auditoria_eventos WHERE id = " + written.eventId());

        IntegrityReport report = verify.verify(VerifyAuditIntegrityCommand.fromConsole());
        assertThat(report.intact()).isFalse();
        assertThat(report.reason()).contains("se borraron eventos");
    }

    @Test
    void ningunaCredencialConfiguradaQuedaGuardada() {
        record.record(AuditEntry.builder(ActorType.PROCESO_EJECUTOR, AuditAction.SESION_OLT_ABIERTA)
                .sistema("OLT RIOHACHA01")
                .peticion(Map.of("usuario", "migrador", "texto", "login migrador Sup3r-Secreta-OLT"))
                .respuesta(Map.of("eco", List.of("User password:Sup3r-Secreta-OLT", "MA5800>")))
                .mensaje("Sesión abierta con Sup3r-Secreta-OLT")
                .build());

        Long leaks = jdbc.queryForObject("""
                SELECT count(*) FROM auditoria_eventos
                WHERE peticion::text LIKE '%Sup3r%' OR respuesta::text LIKE '%Sup3r%' OR mensaje LIKE '%Sup3r%'
                """, Long.class);
        assertThat(leaks).isZero();
        assertThat(verify.verify(VerifyAuditIntegrityCommand.fromConsole()).intact()).isTrue();
    }

    @Test
    void variosHilosRegistrandoALaVezDejanUnaSolaCadenaIntegra() throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            List<Future<?>> tasks = new java.util.ArrayList<>();
            for (int t = 0; t < 8; t++) {
                int thread = t;
                tasks.add(pool.submit(() -> {
                    for (int i = 0; i < 25; i++) {
                        record.record(step("P01", "hilo " + thread + " evento " + i));
                    }
                }));
            }
            for (Future<?> task : tasks) {
                task.get();
            }
        }

        IntegrityReport report = verify.verify(VerifyAuditIntegrityCommand.fromConsole());
        assertThat(report.intact()).as(report.message()).isTrue();
        assertThat(report.checkedEvents()).isEqualTo(200);
    }

    @Test
    void buscarUnSnDevuelveTodosSusEventosEnOrden() {
        record.record(step("P04", "interface gpon 0/1"));
        record.record(AuditEntry.builder(ActorType.PROCESO_EJECUTOR, AuditAction.ONU_TOMADA).sn("HWTC99999999").build());
        record.record(step("P05", "undo ont tr069-server-config 0 33"));

        AuditPage page = query.search(AuditSearchCriteria.bySn("HWTC1A2B3C4D", 0, 50));

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.events()).extracting(e -> e.event().paso()).containsExactly("P04", "P05");
        assertThat(page.events().getFirst().event().respuesta()).containsEntry("lineas", List.of("MA5800(config)#", "ok"));
    }

    private static AuditEntry step(String paso, String comando) {
        return AuditEntry.builder(ActorType.PROCESO_EJECUTOR, AuditAction.COMANDO_RESPONDIDO)
                .usuarioId(3L).loteId(45L).ejecucionId(812L).sn("HWTC1A2B3C4D").paso(paso).sistema("OLT RIOHACHA01")
                .peticion(Map.of("comando", comando))
                .respuesta(Map.of("lineas", List.of("MA5800(config)#", "ok"), "duracionMs", 412, "carga", 0.75))
                .resultado("OK").mensaje("ó á ñ — " + comando).duracionMs(412L)
                .build();
    }

    private String lastAction() {
        return jdbc.queryForObject("SELECT accion FROM auditoria_eventos ORDER BY id DESC LIMIT 1", String.class);
    }

    private void asIntruder(String sql) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET session_replication_role = replica");
                try {
                    statement.execute(sql);
                } finally {
                    statement.execute("SET session_replication_role = origin");
                }
            }
            return null;
        });
    }

    private static Path createTempDir() {
        try {
            return Files.createTempDirectory("anclas-auditoria");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
