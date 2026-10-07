package com.guajiranet.migradoronu.auditoria.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.guajiranet.migradoronu.auditoria.dominio.AuditAnchor;
import com.guajiranet.migradoronu.auditoria.dominio.IntegrityReport;
import com.guajiranet.migradoronu.auditoria.dominio.StoredAuditEvent;
import com.guajiranet.migradoronu.auditoria.puertos.salida.AuditAnchorPort;
import com.guajiranet.migradoronu.comun.puertos.ActorType;
import com.guajiranet.migradoronu.comun.puertos.AuditAction;
import com.guajiranet.migradoronu.comun.puertos.AuditEntry;
import com.guajiranet.migradoronu.comun.seguridad.SecretRedactor;

class CasosDeUsoAuditoriaTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-05T15:00:00.123456789Z"), ZoneOffset.UTC);

    private final InMemoryAuditRepository repository = new InMemoryAuditRepository();
    private final List<AuditAnchor> anchorFile = new ArrayList<>();
    private final AuditAnchorPort anchors = new AuditAnchorPort() {
        @Override public void append(AuditAnchor anchor) { anchorFile.add(anchor); }
        @Override public List<AuditAnchor> readAll() { return List.copyOf(anchorFile); }
    };
    private final RecordAuditEventUseCase record =
            new RecordAuditEventUseCase(repository, () -> List.of("Cl4ve-OLT-Riohacha"), CLOCK);
    private final VerifyAuditIntegrityUseCase verify = new VerifyAuditIntegrityUseCase(repository, anchors, record);

    @Test
    void cadaEventoQuedaEncadenadoAlAnterior() {
        record.record(entry("primero"));
        record.record(entry("segundo"));

        assertThat(repository.rows.get(0).previousHash()).isNull();
        assertThat(repository.rows.get(1).previousHash()).isEqualTo(repository.rows.get(0).hash());
    }

    @Test
    void laHoraEsLaDelRelojRecortadaAMicrosegundos() {
        record.record(entry("x"));

        assertThat(repository.rows.getFirst().event().ocurridoEn()).isEqualTo(Instant.parse("2026-10-05T15:00:00.123456Z"));
    }

    @Test
    void ningunaCredencialLlegaALaAuditoria() {
        record.record(AuditEntry.builder(ActorType.PROCESO_EJECUTOR, AuditAction.COMANDO_RESPONDIDO)
                .peticion(Map.of("comando", "login migrador Cl4ve-OLT-Riohacha", "password", "otra"))
                .respuesta(Map.of("texto", "eco Cl4ve-OLT-Riohacha"))
                .mensaje("falló con Cl4ve-OLT-Riohacha")
                .build());

        StoredAuditEvent stored = repository.rows.getFirst();
        assertThat(stored.event().peticion()).containsEntry("comando", "login migrador " + SecretRedactor.MASK)
                .containsEntry("password", SecretRedactor.MASK);
        assertThat(stored.event().respuesta()).containsEntry("texto", "eco " + SecretRedactor.MASK);
        assertThat(stored.event().mensaje()).doesNotContain("Cl4ve");
    }

    @Test
    void laVerificacionDejaSuResultadoEnLaAuditoria() {
        record.record(entry("a"));
        record.record(entry("b"));

        IntegrityReport report = verify.verify(VerifyAuditIntegrityCommand.fromConsole());

        assertThat(report.intact()).isTrue();
        assertThat(report.checkedEvents()).isEqualTo(2);
        StoredAuditEvent last = repository.rows.getLast();
        assertThat(last.event().accion()).isEqualTo(AuditAction.INTEGRIDAD_VERIFICADA);
        assertThat(last.event().actorTipo()).isEqualTo(ActorType.CONSOLA);
        assertThat(last.event().resultado()).isEqualTo("INTACTA");
    }

    @Test
    void elAnclaApuntaAlUltimoEventoYLaVerificacionLaUsa() {
        AnchorAuditUseCase anchor = new AnchorAuditUseCase(repository, anchors, CLOCK);
        assertThat(anchor.anchorNow(AuditAnchor.DAILY)).isEmpty();

        record.record(entry("a"));
        record.record(entry("b"));
        AuditAnchor written = anchor.anchorNow(AuditAnchor.BATCH_FINISHED).orElseThrow();
        assertThat(written.eventId()).isEqualTo(2);

        repository.rows.removeLast();
        IntegrityReport report = verify.verify(VerifyAuditIntegrityCommand.fromConsole());

        assertThat(report.intact()).isFalse();
        assertThat(report.brokenEventId()).isEqualTo(2);
        assertThat(repository.rows.getLast().event().accion()).isEqualTo(AuditAction.INTEGRIDAD_ROTA);
    }

    @Test
    void laVerificacionRecorreVariasPaginas() {
        for (int i = 0; i < VerifyAuditIntegrityUseCase.PAGE_SIZE + 5; i++) {
            record.record(entry("evento " + i));
        }

        assertThat(verify.verify(VerifyAuditIntegrityCommand.fromConsole()).checkedEvents())
                .isEqualTo(VerifyAuditIntegrityUseCase.PAGE_SIZE + 5);
    }

    private static AuditEntry entry(String mensaje) {
        return AuditEntry.builder(ActorType.USUARIO, AuditAction.LOTE_CONFIRMADO).usuarioId(3L).loteId(45L).mensaje(mensaje).build();
    }
}
