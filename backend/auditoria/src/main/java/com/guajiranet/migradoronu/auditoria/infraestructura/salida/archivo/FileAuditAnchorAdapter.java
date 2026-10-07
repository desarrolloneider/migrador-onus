package com.guajiranet.migradoronu.auditoria.infraestructura.salida.archivo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.guajiranet.migradoronu.auditoria.dominio.AnchorsUnreadableException;
import com.guajiranet.migradoronu.auditoria.dominio.AuditAnchor;
import com.guajiranet.migradoronu.auditoria.puertos.salida.AuditAnchorPort;

/**
 * Anclas en un archivo de solo agregar, una por línea: {@code fecha|motivo|id|huella}. Cada escritura se fuerza al
 * disco antes de volver. En el servidor Linux, el archivo debería además marcarse como solo agregar en el sistema de
 * archivos ({@code chattr +a}) y copiarse con el respaldo diario (arquitectura 11.3).
 */
public class FileAuditAnchorAdapter implements AuditAnchorPort {

    public static final String FILE_NAME = "anclas-auditoria.log";

    private final Path directory;
    private final Path file;

    public FileAuditAnchorAdapter(Path directory) {
        this.directory = directory;
        this.file = directory.resolve(FILE_NAME);
    }

    @Override
    public synchronized void append(AuditAnchor anchor) {
        String line = anchor.anchoredAt() + "|" + anchor.reason() + "|" + anchor.eventId() + "|" + anchor.hash() + "\n";
        try {
            Files.createDirectories(directory);
            try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND)) {
                channel.write(ByteBuffer.wrap(line.getBytes(StandardCharsets.UTF_8)));
                channel.force(true);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo escribir el ancla de la auditoría en " + file, e);
        }
    }

    @Override
    public List<AuditAnchor> readAll() {
        if (!Files.exists(file)) {
            return List.of();
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AnchorsUnreadableException("no se pudo leer " + file, e);
        }
        List<AuditAnchor> anchors = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\\|", -1);
            try {
                if (parts.length != 4) {
                    throw new IllegalArgumentException("se esperaban 4 campos y hay " + parts.length);
                }
                anchors.add(new AuditAnchor(Instant.parse(parts[0]), parts[1], Long.parseLong(parts[2]), parts[3]));
            } catch (RuntimeException e) {
                throw new AnchorsUnreadableException("la línea " + (i + 1) + " de " + file + " no se reconoce (" + e.getMessage() + ")", e);
            }
        }
        return anchors;
    }
}
