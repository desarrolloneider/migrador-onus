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

--changeset migrador:acceso-002-debe-cambiar-clave
--comment: El usuario inicial (del .env) entra con una contraseña conocida y la pantalla le exige cambiarla antes de hacer cualquier otra cosa.
ALTER TABLE usuarios ADD COLUMN debe_cambiar_clave BOOLEAN NOT NULL DEFAULT FALSE;
--rollback ALTER TABLE usuarios DROP COLUMN debe_cambiar_clave;

--changeset migrador:acceso-003-permisos
--comment: Gestión de usuarios desde la pantalla: administrador y módulos asignados. Los usuarios que ya existían (admin) quedan como administradores para no perder acceso.
ALTER TABLE usuarios ADD COLUMN es_administrador BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE usuarios ADD COLUMN modulos JSONB NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(modulos) = 'array');
UPDATE usuarios SET es_administrador = TRUE WHERE usuario <> 'verificacion';
UPDATE usuarios SET modulos = '["LOTES","EJECUTAR","HISTORIAL","FALLIDAS","AUDITORIA","CONFIGURACION"]'::jsonb WHERE usuario = 'verificacion';
--rollback ALTER TABLE usuarios DROP COLUMN modulos; ALTER TABLE usuarios DROP COLUMN es_administrador;
