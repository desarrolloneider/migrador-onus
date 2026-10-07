--liquibase formatted sql

--changeset migrador:acceso-001-usuarios
--comment: Usuarios de la herramienta (ESP-01, ESP-02). Ningún usuario se borra: se desactiva.
CREATE TABLE usuarios (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario           VARCHAR(60)  NOT NULL CHECK (usuario ~ '^[^[:space:]]+$'),
    nombre            VARCHAR(120) NOT NULL,
    hash_clave        VARCHAR(255) NOT NULL,
    activo            BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en         TIMESTAMPTZ NOT NULL DEFAULT now(),
    ultimo_ingreso_en TIMESTAMPTZ,
    intentos_fallidos INTEGER NOT NULL DEFAULT 0 CHECK (intentos_fallidos >= 0),
    bloqueado_hasta   TIMESTAMPTZ,
    CONSTRAINT uk_usuarios_usuario UNIQUE (usuario)
);
--rollback DROP TABLE usuarios;
