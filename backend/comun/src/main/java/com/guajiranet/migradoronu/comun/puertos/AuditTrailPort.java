package com.guajiranet.migradoronu.comun.puertos;

/**
 * Puerto transversal de auditoría. Lo usan todos los módulos y lo implementa un solo adaptador en ensamblaje,
 * que llama al módulo auditoria (corrección 2a, docs/adr/0002).
 *
 * <p>Devuelve solo cuando el evento quedó confirmado en la base (RN-11: la intención se registra antes de mandar
 * el comando). Si la base no está disponible lanza {@link AuditUnavailableException} y quien llama no debe actuar
 * (RN-30).
 */
public interface AuditTrailPort {

    void record(AuditEntry entry);
}
