package com.guajiranet.migradoronu.acceso.infraestructura.entrada.seguridad;

import java.util.Optional;

import com.guajiranet.migradoronu.acceso.dominio.Modulo;
import com.guajiranet.migradoronu.acceso.dominio.Permisos;

/**
 * Qué módulo exige cada dirección de la API. Lo que no está aquí lo puede usar cualquiera con sesión: inicio de
 * sesión, su propia contraseña, el estado del sistema y las listas de solo consulta que usan varias pantallas.
 */
final class ReglasDePermiso {

    /** El permiso que hace falta: un módulo o ser administrador. */
    record Exigencia(Modulo modulo, boolean administrador, String descripcion) {

        boolean cumple(Permisos permisos) {
            return administrador ? permisos.administrador() : permisos.puede(modulo);
        }
    }

    private ReglasDePermiso() {
    }

    static Optional<Exigencia> para(String metodo, String ruta) {
        boolean lectura = "GET".equals(metodo);
        if (ruta.startsWith("/api/v1/users")) {
            return Optional.of(new Exigencia(null, true, "gestionar usuarios"));
        }
        // Devolver ONUs con la VLAN 46 activa: solo administradores.
        if (ruta.startsWith("/api/v1/vlan-gestion") || ruta.startsWith("/api/v1/batches/remove-vlan-preview")) {
            return Optional.of(new Exigencia(null, true, "quitar la VLAN 46"));
        }
        if (ruta.startsWith("/api/v1/batches")) {
            if (!lectura && (ruta.endsWith("/confirm") || ruta.endsWith("/stop") || ruta.endsWith("/repeat-real")
                    || ruta.endsWith("/retry-failed"))) {
                return modulo(Modulo.EJECUTAR, "ejecutar o detener lotes");
            }
            return modulo(Modulo.LOTES, "ver o armar lotes");
        }
        if (ruta.startsWith("/api/v1/olt-status")) {
            return modulo(Modulo.LOTES, "ver el estado de las OLT");
        }
        if (ruta.matches("/api/v1/olts/[^/]+/onus")) {
            return modulo(Modulo.LOTES, "ver las ONUs de las OLT");
        }
        if (ruta.startsWith("/api/v1/onu-runs")) {
            if (ruta.endsWith("/failed") || ruta.endsWith("/review") || ruta.endsWith("/review-all")) {
                return modulo(Modulo.FALLIDAS, "revisar fallidas");
            }
            return modulo(Modulo.LOTES, "ver ejecuciones");
        }
        if (ruta.startsWith("/api/v1/onu-history")) {
            return modulo(Modulo.HISTORIAL, "ver el historial de ONUs");
        }
        if (ruta.startsWith("/api/v1/audit")) {
            return modulo(Modulo.AUDITORIA, "ver la auditoría");
        }
        if (!lectura && (ruta.startsWith("/api/v1/settings") || ruta.startsWith("/api/v1/olts"))) {
            return modulo(Modulo.CONFIGURACION, "cambiar la configuración");
        }
        return Optional.empty();
    }

    private static Optional<Exigencia> modulo(Modulo modulo, String descripcion) {
        return Optional.of(new Exigencia(modulo, false, descripcion));
    }
}
