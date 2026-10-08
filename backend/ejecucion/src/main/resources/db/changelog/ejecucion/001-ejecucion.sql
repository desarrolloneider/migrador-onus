--liquibase formatted sql

--changeset migrador:ejecucion-001-procesos-ejecutores
--comment: Latido del proceso ejecutor (arquitectura 9.1).
CREATE TABLE procesos_ejecutores (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    host             VARCHAR(255) NOT NULL,
    pid              BIGINT NOT NULL,
    iniciado_en      TIMESTAMPTZ NOT NULL DEFAULT now(),
    ultimo_latido_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    detenido_en      TIMESTAMPTZ
);
--rollback DROP TABLE procesos_ejecutores;

--changeset migrador:ejecucion-001-ejecuciones-onu
--comment: Una ejecución por ONU de un lote confirmado. También es la cola de trabajo (arquitectura 9.1).
CREATE TABLE ejecuciones_onu (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lote_id               BIGINT NOT NULL REFERENCES lotes (id),
    lote_item_id          BIGINT NOT NULL REFERENCES lote_items (id),
    sn                    VARCHAR(64) NOT NULL,
    orden                 INTEGER NOT NULL CHECK (orden >= 0),
    estado                VARCHAR(25) NOT NULL CHECK (estado IN ('PENDIENTE', 'REVALIDANDO', 'EN_CURSO', 'COMPLETADA',
                                                                'FALLIDA', 'EXCLUIDA_AL_EJECUTAR', 'NO_INICIADA')),
    paso_actual           VARCHAR(3) CHECK (paso_actual ~ '^P0[1-8]$'),
    codigo_fallo          VARCHAR(40),
    mensaje_fallo         TEXT,
    fotografia_previa     JSONB,
    estado_al_fallar      JSONB,
    cambios_aplicados     JSONB NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(cambios_aplicados) = 'array'),
    procedimiento_version VARCHAR(40) NOT NULL,
    proceso_id            BIGINT REFERENCES procesos_ejecutores (id),
    iniciada_en           TIMESTAMPTZ,
    terminada_en          TIMESTAMPTZ,
    revisada              BOOLEAN NOT NULL DEFAULT FALSE,
    revisada_por          BIGINT REFERENCES usuarios (id),
    revisada_en           TIMESTAMPTZ,
    nota_revision         TEXT,
    CONSTRAINT uk_ejecuciones_onu_item UNIQUE (lote_item_id),
    -- RN-06: toda fallida dice en qué paso se detuvo, con qué código y por qué.
    CONSTRAINT ck_ejecuciones_onu_fallida CHECK (estado <> 'FALLIDA'
                                                 OR (paso_actual IS NOT NULL AND codigo_fallo IS NOT NULL AND mensaje_fallo IS NOT NULL)),
    -- RN-26 y spec 8.1: solo una fallida se revisa, con quién, cuándo y una nota de mínimo 10 caracteres.
    CONSTRAINT ck_ejecuciones_onu_revision CHECK (NOT revisada
                                                  OR (estado = 'FALLIDA' AND revisada_por IS NOT NULL AND revisada_en IS NOT NULL
                                                      AND char_length(btrim(nota_revision)) >= 10))
);

-- RN-16: una ONU solo puede estar en un lote activo a la vez.
CREATE UNIQUE INDEX uk_ejecuciones_onu_sn_activa ON ejecuciones_onu (sn)
    WHERE estado IN ('PENDIENTE', 'REVALIDANDO', 'EN_CURSO');
-- Cola de trabajo: la siguiente pendiente por lote y orden (SKIP LOCKED).
CREATE INDEX ix_ejecuciones_onu_cola ON ejecuciones_onu (lote_id, orden) WHERE estado = 'PENDIENTE';
-- K11 y ESP-14: fallidas sin revisar.
CREATE INDEX ix_ejecuciones_onu_sin_revisar ON ejecuciones_onu (sn) WHERE estado = 'FALLIDA' AND NOT revisada;
--rollback DROP TABLE ejecuciones_onu;

--changeset migrador:ejecucion-001-pasos-ejecucion
--comment: Cada paso ejecutado, con comando, respuesta de la OLT y verificación (ESP-09).
CREATE TABLE pasos_ejecucion (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ejecucion_id  BIGINT NOT NULL REFERENCES ejecuciones_onu (id),
    paso          VARCHAR(3) NOT NULL CHECK (paso ~ '^P0[1-8]$'),
    estado        VARCHAR(15) NOT NULL CHECK (estado IN ('PENDIENTE', 'EN_CURSO', 'CORRECTO', 'YA_ESTABA', 'FALLIDO',
                                                         'NO_EJECUTADO', 'SIMULADO')),
    olt_id        BIGINT REFERENCES olts (id),
    comando       TEXT,
    -- Recortada a 64 KB (arquitectura 12). El texto completo queda en la auditoría.
    respuesta_olt TEXT CHECK (octet_length(respuesta_olt) <= 65536),
    iniciado_en   TIMESTAMPTZ,
    terminado_en  TIMESTAMPTZ,
    duracion_ms   BIGINT CHECK (duracion_ms >= 0),
    esperado      JSONB,
    encontrado    JSONB,
    codigo        VARCHAR(40),
    mensaje       TEXT,
    CONSTRAINT uk_pasos_ejecucion UNIQUE (ejecucion_id, paso)
);
--rollback DROP TABLE pasos_ejecucion;

--changeset migrador:ejecucion-001-historial-onus
--comment: Último resultado por ONU (ESP-17). Los intentos completos se leen de ejecuciones_onu.
CREATE TABLE historial_onus (
    sn                  VARCHAR(64) PRIMARY KEY,
    ip                  VARCHAR(45),
    olt_id              BIGINT REFERENCES olts (id),
    tarjeta             VARCHAR(10),
    pon                 INTEGER,
    onu_id              INTEGER,
    ultimo_resultado    VARCHAR(16) NOT NULL CHECK (ultimo_resultado IN ('CORREGIDA', 'FALLIDA', 'EXCLUIDA', 'YA_TENIA_PERFIL')),
    ultimo_lote_id      BIGINT NOT NULL REFERENCES lotes (id),
    ultima_ejecucion_id BIGINT REFERENCES ejecuciones_onu (id),
    codigo              VARCHAR(40),
    actualizado_en      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_historial_onus_resultado ON historial_onus (ultimo_resultado, olt_id);
--rollback DROP TABLE historial_onus;

--changeset migrador:ejecucion-002-fallidas-resueltas
--comment: Una fallida queda resuelta cuando la misma ONU se cambia después con éxito en un lote real (o ya tenía el perfil). Así no se ofrece reintentarla ni se repite en "Fallidas por revisar".
ALTER TABLE ejecuciones_onu ADD COLUMN resuelta_en_lote_id BIGINT REFERENCES lotes (id);

UPDATE ejecuciones_onu f
SET revisada            = TRUE,
    revisada_por        = coalesce(f.revisada_por, l2.confirmado_por),
    revisada_en         = coalesce(f.revisada_en, e2.terminada_en),
    nota_revision       = coalesce(f.nota_revision, 'Resuelta: la ONU se cambió después en el lote ' || l2.numero || '.'),
    resuelta_en_lote_id = e2.lote_id
FROM (SELECT DISTINCT ON (e.sn) e.sn, e.lote_id, e.terminada_en
      FROM ejecuciones_onu e JOIN lotes l ON l.id = e.lote_id
      WHERE NOT l.simulacion AND (e.estado = 'COMPLETADA' OR (e.estado = 'EXCLUIDA_AL_EJECUTAR' AND e.codigo_fallo IS NULL))
      ORDER BY e.sn, e.terminada_en DESC) e2
JOIN lotes l2 ON l2.id = e2.lote_id
WHERE f.sn = e2.sn AND f.estado = 'FALLIDA' AND f.resuelta_en_lote_id IS NULL AND f.terminada_en < e2.terminada_en;
--rollback ALTER TABLE ejecuciones_onu DROP COLUMN resuelta_en_lote_id;
