package com.guajiranet.migradoronu.auditoria.dominio;

import static com.guajiranet.migradoronu.auditoria.dominio.AuditFixtures.T0;
import static com.guajiranet.migradoronu.auditoria.dominio.AuditFixtures.chain;
import static com.guajiranet.migradoronu.auditoria.dominio.AuditFixtures.event;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ChainVerifierTest {

    @Test
    void unaCadenaSinCambiosEstaIntegra() {
        IntegrityReport report = verify(chain(5), List.of());

        assertThat(report.intact()).isTrue();
        assertThat(report.checkedEvents()).isEqualTo(5);
        assertThat(report.lastEventId()).isEqualTo(5);
        assertThat(report.lastHash()).isEqualTo(chain(5).get(4).hash());
    }

    @Test
    void detectaUnEventoModificado() {
        List<StoredAuditEvent> rows = new ArrayList<>(chain(5));
        StoredAuditEvent third = rows.get(2);
        rows.set(2, StoredAuditEvent.readable(3, third.previousHash(), third.hash(), event(T0.plusSeconds(3), "alterado")));

        IntegrityReport report = verify(rows, List.of());

        assertThat(report.intact()).isFalse();
        assertThat(report.brokenEventId()).isEqualTo(3);
        assertThat(report.reason()).contains("se modificó");
    }

    @Test
    void detectaUnEventoQuitadoDelMedio() {
        List<StoredAuditEvent> rows = new ArrayList<>(chain(5));
        rows.remove(2);

        IntegrityReport report = verify(rows, List.of());

        assertThat(report.brokenEventId()).isEqualTo(4);
        assertThat(report.reason()).contains("huella anterior no coincide");
    }

    @Test
    void detectaEventosQuitadosDelPrincipio() {
        IntegrityReport report = verify(chain(5).subList(2, 5), List.of());

        assertThat(report.brokenEventId()).isEqualTo(3);
        assertThat(report.reason()).contains("del principio");
    }

    @Test
    void conUnAnclaDetectaEventosQuitadosDelFinal() {
        List<StoredAuditEvent> full = chain(5);
        AuditAnchor anchor = new AuditAnchor(T0, AuditAnchor.DAILY, 5, full.get(4).hash());

        IntegrityReport report = verify(full.subList(0, 3), List.of(anchor));

        assertThat(report.intact()).isFalse();
        assertThat(report.brokenEventId()).isEqualTo(5);
        assertThat(report.reason()).contains("se borraron eventos");
    }

    @Test
    void conUnAnclaDetectaLaCadenaReescritaCompleta() {
        AuditAnchor anchor = new AuditAnchor(T0, AuditAnchor.DAILY, 2, chain(2).get(1).hash());
        List<StoredAuditEvent> rewritten = new ArrayList<>();
        String previous = null;
        for (int i = 1; i <= 3; i++) {
            SealedAuditEvent sealed = HashChain.seal(previous, event(T0.plusSeconds(i), "reescrito " + i));
            rewritten.add(StoredAuditEvent.readable(i, sealed.previousHash(), sealed.hash(), sealed.event()));
            previous = sealed.hash();
        }

        IntegrityReport report = verify(rewritten, List.of(anchor));

        assertThat(report.brokenEventId()).isEqualTo(2);
        assertThat(report.reason()).contains("se reescribió");
    }

    @Test
    void unaFilaIlegibleSeInformaComoAlteracion() {
        List<StoredAuditEvent> rows = new ArrayList<>(chain(2));
        rows.add(StoredAuditEvent.unreadable(3, rows.get(1).hash(), "b".repeat(64), "No enum constant ACCION_FALSA"));

        IntegrityReport report = verify(rows, List.of());

        assertThat(report.brokenEventId()).isEqualTo(3);
        assertThat(report.message()).contains("NO está íntegra").contains("ACCION_FALSA");
    }

    private static IntegrityReport verify(List<StoredAuditEvent> rows, List<AuditAnchor> anchors) {
        ChainVerifier verifier = new ChainVerifier(anchors);
        for (StoredAuditEvent row : rows) {
            if (!verifier.accept(row)) {
                break;
            }
        }
        return verifier.finish();
    }
}
