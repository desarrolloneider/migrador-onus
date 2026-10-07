package com.guajiranet.migradoronu.auditoria.aplicacion;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Objects;

import com.guajiranet.migradoronu.auditoria.dominio.AuditEvent;
import com.guajiranet.migradoronu.auditoria.dominio.HashChain;
import com.guajiranet.migradoronu.auditoria.puertos.entrada.RecordAuditEventPort;
import com.guajiranet.migradoronu.auditoria.puertos.salida.AuditRepositoryPort;
import com.guajiranet.migradoronu.auditoria.puertos.salida.KnownSecretsPort;
import com.guajiranet.migradoronu.comun.puertos.AuditEntry;
import com.guajiranet.migradoronu.comun.seguridad.SecretRedactor;

/**
 * Guarda un evento encadenado (RN-11). Antes de sellarlo quita las credenciales (RN-25): por nombre de clave en la
 * petición y la respuesta, y por valor en todos los textos. La hora la pone la herramienta, en UTC (RN-21).
 */
public class RecordAuditEventUseCase implements RecordAuditEventPort {

    private final AuditRepositoryPort repository;
    private final KnownSecretsPort knownSecrets;
    private final Clock clock;

    public RecordAuditEventUseCase(AuditRepositoryPort repository, KnownSecretsPort knownSecrets, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.knownSecrets = Objects.requireNonNull(knownSecrets, "knownSecrets");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void record(AuditEntry entry) {
        Collection<String> secrets = knownSecrets.secretValues();
        AuditEvent event = new AuditEvent(
                clock.instant().truncatedTo(ChronoUnit.MICROS),
                entry.actorTipo(),
                entry.usuarioId(),
                entry.ipOrigen(),
                entry.accion(),
                entry.loteId(),
                entry.ejecucionId(),
                entry.sn(),
                entry.paso(),
                entry.sistema(),
                SecretRedactor.redact(entry.peticion(), secrets),
                SecretRedactor.redact(entry.respuesta(), secrets),
                SecretRedactor.redact(entry.resultado(), secrets),
                entry.codigo(),
                SecretRedactor.redact(entry.mensaje(), secrets),
                entry.duracionMs());
        repository.append(previousHash -> HashChain.seal(previousHash, event));
    }
}
