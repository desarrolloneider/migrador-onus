package com.guajiranet.migradoronu.comun.puertos;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Un evento para la auditoría (RN-11, arquitectura 10.1). Las huellas de la cadena las calcula el módulo
 * auditoria al guardarlo. Quien lo arma no debe incluir credenciales (RN-25); aun así, auditoria vuelve a
 * pasar la petición y la respuesta por {@code SecretRedactor} antes de guardar.
 */
public record AuditEntry(
        ActorType actorTipo,
        Long usuarioId,
        String ipOrigen,
        AuditAction accion,
        Long loteId,
        Long ejecucionId,
        String sn,
        String paso,
        String sistema,
        Map<String, Object> peticion,
        Map<String, Object> respuesta,
        String resultado,
        String codigo,
        String mensaje,
        Long duracionMs) {

    public AuditEntry {
        Objects.requireNonNull(actorTipo, "actorTipo");
        Objects.requireNonNull(accion, "accion");
        // Un intento fallido con un usuario que no existe se registra sin id: solo se sabe qué escribió.
        if (actorTipo == ActorType.USUARIO && usuarioId == null && accion != AuditAction.SESION_FALLIDA) {
            throw new IllegalArgumentException("Un evento hecho por un usuario debe decir cuál usuario (RN-11)");
        }
        if (duracionMs != null && duracionMs < 0) {
            throw new IllegalArgumentException("La duración no puede ser negativa: " + duracionMs);
        }
        peticion = copy(peticion);
        respuesta = copy(respuesta);
    }

    public static Builder builder(ActorType actorTipo, AuditAction accion) {
        return new Builder(actorTipo, accion);
    }

    // Las respuestas de los sistemas pueden traer valores nulos, por eso no se usa Map.copyOf.
    private static Map<String, Object> copy(Map<String, Object> values) {
        return values == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public static final class Builder {
        private final ActorType actorTipo;
        private final AuditAction accion;
        private Long usuarioId;
        private String ipOrigen;
        private Long loteId;
        private Long ejecucionId;
        private String sn;
        private String paso;
        private String sistema;
        private Map<String, Object> peticion;
        private Map<String, Object> respuesta;
        private String resultado;
        private String codigo;
        private String mensaje;
        private Long duracionMs;

        private Builder(ActorType actorTipo, AuditAction accion) {
            this.actorTipo = actorTipo;
            this.accion = accion;
        }

        public Builder usuarioId(Long usuarioId) { this.usuarioId = usuarioId; return this; }
        public Builder ipOrigen(String ipOrigen) { this.ipOrigen = ipOrigen; return this; }
        public Builder loteId(Long loteId) { this.loteId = loteId; return this; }
        public Builder ejecucionId(Long ejecucionId) { this.ejecucionId = ejecucionId; return this; }
        public Builder sn(String sn) { this.sn = sn; return this; }
        public Builder paso(String paso) { this.paso = paso; return this; }
        public Builder sistema(String sistema) { this.sistema = sistema; return this; }
        public Builder peticion(Map<String, Object> peticion) { this.peticion = peticion; return this; }
        public Builder respuesta(Map<String, Object> respuesta) { this.respuesta = respuesta; return this; }
        public Builder resultado(String resultado) { this.resultado = resultado; return this; }
        public Builder codigo(String codigo) { this.codigo = codigo; return this; }
        public Builder mensaje(String mensaje) { this.mensaje = mensaje; return this; }
        public Builder duracionMs(Long duracionMs) { this.duracionMs = duracionMs; return this; }

        public AuditEntry build() {
            return new AuditEntry(actorTipo, usuarioId, ipOrigen, accion, loteId, ejecucionId, sn, paso, sistema,
                    peticion, respuesta, resultado, codigo, mensaje, duracionMs);
        }
    }
}
