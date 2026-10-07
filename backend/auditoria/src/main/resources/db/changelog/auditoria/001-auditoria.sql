--liquibase formatted sql

--changeset migrador:auditoria-001-eventos
--comment: Auditoría de solo inserción con cadena de huellas (arquitectura 10). Sin llaves foráneas a propósito: registrar un evento nunca debe fallar por el estado de otra tabla.
CREATE TABLE auditoria_eventos (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ocurrido_en   TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor_tipo    VARCHAR(20) NOT NULL CHECK (actor_tipo IN ('USUARIO', 'PROCESO_EJECUTOR', 'CONSOLA')),
    usuario_id    BIGINT,
    ip_origen     VARCHAR(45),
    accion        VARCHAR(40) NOT NULL,
    lote_id       BIGINT,
    ejecucion_id  BIGINT,
    sn            VARCHAR(64),
    paso          VARCHAR(3),
    sistema       VARCHAR(60),
    peticion      JSONB,
    respuesta     JSONB,
    resultado     VARCHAR(20),
    codigo        VARCHAR(40),
    mensaje       TEXT,
    duracion_ms   BIGINT CHECK (duracion_ms >= 0),
    hash_anterior CHAR(64) CHECK (hash_anterior ~ '^[0-9a-f]{64}$'),
    hash          CHAR(64) NOT NULL CHECK (hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT uk_auditoria_eventos_hash UNIQUE (hash)
);

CREATE INDEX ix_auditoria_eventos_fecha ON auditoria_eventos (ocurrido_en);
CREATE INDEX ix_auditoria_eventos_sn ON auditoria_eventos (sn, ocurrido_en);
CREATE INDEX ix_auditoria_eventos_lote ON auditoria_eventos (lote_id, ocurrido_en);
--rollback DROP TABLE auditoria_eventos;

--changeset migrador:auditoria-001-funcion-inalterable splitStatements:false
--comment: RNF-02. La función que rechaza todo cambio o borrado.
CREATE FUNCTION auditoria_eventos_inalterable() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'auditoria_eventos es de solo inserción: % no está permitido (RNF-02)', TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$;
--rollback DROP FUNCTION auditoria_eventos_inalterable();

--changeset migrador:auditoria-001-disparadores
CREATE TRIGGER tr_auditoria_eventos_sin_cambios
    BEFORE UPDATE OR DELETE ON auditoria_eventos
    FOR EACH ROW EXECUTE FUNCTION auditoria_eventos_inalterable();

CREATE TRIGGER tr_auditoria_eventos_sin_truncate
    BEFORE TRUNCATE ON auditoria_eventos
    FOR EACH STATEMENT EXECUTE FUNCTION auditoria_eventos_inalterable();
--rollback DROP TRIGGER tr_auditoria_eventos_sin_truncate ON auditoria_eventos; DROP TRIGGER tr_auditoria_eventos_sin_cambios ON auditoria_eventos;

--changeset migrador:auditoria-002-permisos-app
--comment: La aplicación (migrador_app) solo inserta y lee la auditoría (arquitectura 10.2). Se aplica cuando el rol existe. Si no existe todavía, se reintenta en la siguiente migración.
--preconditions onFail:CONTINUE
--precondition-sql-check expectedResult:1 SELECT count(*) FROM pg_roles WHERE rolname = 'migrador_app'
REVOKE UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER ON auditoria_eventos FROM migrador_app;
GRANT SELECT, INSERT ON auditoria_eventos TO migrador_app;
