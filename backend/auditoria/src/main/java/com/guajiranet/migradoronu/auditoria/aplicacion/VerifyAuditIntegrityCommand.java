package com.guajiranet.migradoronu.auditoria.aplicacion;

import java.util.Objects;

import com.guajiranet.migradoronu.comun.puertos.ActorType;

/** Quién pide la verificación: un operador desde la pantalla o un administrador por consola. */
public record VerifyAuditIntegrityCommand(ActorType actor, Long usuarioId, String ipOrigen) {

    public VerifyAuditIntegrityCommand {
        Objects.requireNonNull(actor, "actor");
        if (actor == ActorType.USUARIO && usuarioId == null) {
            throw new IllegalArgumentException("Una verificación pedida por un usuario debe decir cuál usuario");
        }
    }

    public static VerifyAuditIntegrityCommand fromConsole() {
        return new VerifyAuditIntegrityCommand(ActorType.CONSOLA, null, null);
    }
}
