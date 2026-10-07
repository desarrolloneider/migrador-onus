--liquibase formatted sql

--changeset migrador:comun-001-claves-idempotencia
--comment: RN-24. Toda orden que cambia un estado se ejecuta una sola vez aunque llegue repetida. Se limpian a las 24 horas.
CREATE TABLE claves_idempotencia (
    clave            VARCHAR(100) NOT NULL,
    usuario_id       BIGINT NOT NULL,
    ruta             VARCHAR(200) NOT NULL,
    codigo_respuesta INTEGER NOT NULL,
    respuesta        JSONB,
    creada_en        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_claves_idempotencia PRIMARY KEY (usuario_id, clave)
);

CREATE INDEX ix_claves_idempotencia_creada ON claves_idempotencia (creada_en);
--rollback DROP TABLE claves_idempotencia;
