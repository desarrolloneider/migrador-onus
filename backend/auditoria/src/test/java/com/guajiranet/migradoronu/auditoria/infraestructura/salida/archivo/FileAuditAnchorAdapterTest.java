package com.guajiranet.migradoronu.auditoria.infraestructura.salida.archivo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.guajiranet.migradoronu.auditoria.dominio.AnchorsUnreadableException;
import com.guajiranet.migradoronu.auditoria.dominio.AuditAnchor;

class FileAuditAnchorAdapterTest {

    @TempDir
    Path directory;

    @Test
    void agregaAnclasSinBorrarLasAnterioresYLasLeeEnOrden() {
        FileAuditAnchorAdapter adapter = new FileAuditAnchorAdapter(directory.resolve("anclas"));
        AuditAnchor first = new AuditAnchor(Instant.parse("2026-10-05T05:00:00Z"), AuditAnchor.DAILY, 10, "a".repeat(64));
        AuditAnchor second = new AuditAnchor(Instant.parse("2026-10-05T16:30:00.250Z"), AuditAnchor.BATCH_FINISHED, 42, "b".repeat(64));

        assertThat(adapter.readAll()).isEmpty();
        adapter.append(first);
        adapter.append(second);

        assertThat(new FileAuditAnchorAdapter(directory.resolve("anclas")).readAll()).containsExactly(first, second);
    }

    @Test
    void unaLineaQueNoSeReconoceHaceIlegibleElArchivo() throws Exception {
        FileAuditAnchorAdapter adapter = new FileAuditAnchorAdapter(directory);
        adapter.append(new AuditAnchor(Instant.parse("2026-10-05T05:00:00Z"), AuditAnchor.DAILY, 10, "a".repeat(64)));
        Files.writeString(directory.resolve(FileAuditAnchorAdapter.FILE_NAME), "alterado|x\n", StandardOpenOption.APPEND);

        assertThatThrownBy(adapter::readAll)
                .isInstanceOf(AnchorsUnreadableException.class)
                .hasMessageContaining("línea 2");
    }
}
