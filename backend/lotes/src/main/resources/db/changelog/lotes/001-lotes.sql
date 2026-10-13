--liquibase formatted sql

--changeset migrador:lotes-001-lotes
--comment: Lotes y pruebas individuales (ESP-03 a ESP-05, ESP-10). La simulación es una marca del lote (ESP-11).
CREATE SEQUENCE lotes_numero_seq START WITH 1 INCREMENT BY 1 NO CYCLE;

CREATE TABLE lotes (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    numero                VARCHAR(20) NOT NULL CHECK (numero ~ '^LOT-[0-9]{4}-[0-9]{6}$'),
    modo                  VARCHAR(12) NOT NULL CHECK (modo IN ('LOTE', 'INDIVIDUAL')),
    simulacion            BOOLEAN NOT NULL DEFAULT FALSE,
    marca                 VARCHAR(40) NOT NULL,
    filtro_olt            VARCHAR(40),
    filtro_tarjeta        VARCHAR(10),
    filtro_pon            INTEGER CHECK (filtro_pon >= 0),
    estado                VARCHAR(25) NOT NULL CHECK (estado IN ('VISTA_PREVIA', 'CONFIRMADO', 'EN_EJECUCION', 'DETENIENDOSE',
                                                                'TERMINADO', 'TERMINADO_CON_FALLAS', 'DETENIDO', 'DESCARTADO')),
    creado_por            BIGINT NOT NULL REFERENCES usuarios (id),
    creado_en             TIMESTAMPTZ NOT NULL DEFAULT now(),
    vence_en              TIMESTAMPTZ NOT NULL,
    confirmado_por        BIGINT REFERENCES usuarios (id),
    confirmado_en         TIMESTAMPTZ,
    iniciado_en           TIMESTAMPTZ,
    terminado_en          TIMESTAMPTZ,
    detenido_por          BIGINT REFERENCES usuarios (id),
    motivo_detencion      TEXT,
    parametros_usados     JSONB,
    procedimiento_version VARCHAR(40),
    contadores            JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT uk_lotes_numero UNIQUE (numero),
    -- ESP-03: para escoger tarjeta hay que escoger OLT; para escoger puerto hay que escoger tarjeta.
    CONSTRAINT ck_lotes_filtros CHECK ((filtro_tarjeta IS NULL OR filtro_olt IS NOT NULL)
                                       AND (filtro_pon IS NULL OR filtro_tarjeta IS NOT NULL)),
    -- RN-36 y RN-37: todo lote que pasó de la vista previa tiene quién lo confirmó y qué parámetros y versión congeló.
    CONSTRAINT ck_lotes_confirmacion CHECK (estado IN ('VISTA_PREVIA', 'DESCARTADO')
                                            OR (confirmado_por IS NOT NULL AND confirmado_en IS NOT NULL
                                                AND parametros_usados IS NOT NULL AND procedimiento_version IS NOT NULL))
);

CREATE INDEX ix_lotes_estado ON lotes (estado, creado_en DESC);
--rollback DROP TABLE lotes; DROP SEQUENCE lotes_numero_seq;

--changeset migrador:lotes-001-lote-items
--comment: Una fila por ONU de la vista previa, en uno de los tres grupos (ESP-04).
CREATE TABLE lote_items (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lote_id              BIGINT NOT NULL REFERENCES lotes (id),
    sn                   VARCHAR(64) NOT NULL,
    ip                   VARCHAR(45),
    grupo                VARCHAR(16) NOT NULL CHECK (grupo IN ('ENTRA', 'EXCLUIDA', 'YA_TIENE_PERFIL')),
    codigo_motivo        VARCHAR(40),
    mensaje              TEXT,
    cliente              VARCHAR(200),
    contrato             VARCHAR(60),
    marca                VARCHAR(40),
    modelo               VARCHAR(60),
    -- olt_codigo es el que reporta SmartOLT. olt_id queda vacío si esa OLT no está registrada (EXC_OLT_SIN_ACCESO_CONFIGURADO).
    olt_codigo           VARCHAR(40) NOT NULL,
    olt_id               BIGINT REFERENCES olts (id),
    tarjeta              VARCHAR(10),
    pon                  INTEGER CHECK (pon >= 0),
    onu_id               INTEGER CHECK (onu_id >= 0),
    perfil_actual        VARCHAR(80),
    registros_duplicados JSONB NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(registros_duplicados) = 'array'),
    orden                INTEGER NOT NULL CHECK (orden >= 0),
    CONSTRAINT uk_lote_items_lote_sn UNIQUE (lote_id, sn),
    -- RN-04 y DEC-08: toda excluida lleva código y mensaje, y solo las excluidas.
    CONSTRAINT ck_lote_items_motivo CHECK ((grupo = 'EXCLUIDA') = (codigo_motivo IS NOT NULL AND mensaje IS NOT NULL)),
    -- RN-14: una ONU que entra tiene IP y posición completas, nunca supuestas.
    CONSTRAINT ck_lote_items_entra CHECK (grupo <> 'ENTRA'
                                          OR (ip IS NOT NULL AND olt_id IS NOT NULL AND tarjeta IS NOT NULL
                                              AND pon IS NOT NULL AND onu_id IS NOT NULL))
);

CREATE INDEX ix_lote_items_sn ON lote_items (sn);
--rollback DROP TABLE lote_items;

--changeset migrador:lotes-010-entra-sin-ip
--comment: Una ONU en línea sin IP o sin contrato en Wispro también puede entrar (pedido del 9 de octubre): la OLT la cambia por posición y SN.
ALTER TABLE lote_items DROP CONSTRAINT ck_lote_items_entra;
ALTER TABLE lote_items ADD CONSTRAINT ck_lote_items_entra CHECK (grupo <> 'ENTRA' OR (olt_id IS NOT NULL AND tarjeta IS NOT NULL AND pon IS NOT NULL AND onu_id IS NOT NULL));
--rollback ALTER TABLE lote_items DROP CONSTRAINT ck_lote_items_entra;

--changeset migrador:lotes-011-tipo-de-lote
--comment: Qué cambia el lote: el perfil TR-069 en la OLT (como hasta ahora) o la IP de gestión DHCP en la VLAN 46 en SmartOLT (procedimiento del NOC, 13 de octubre).
ALTER TABLE lotes ADD COLUMN tipo VARCHAR(20) NOT NULL DEFAULT 'PERFIL_TR069';
ALTER TABLE lotes ADD CONSTRAINT ck_lotes_tipo CHECK (tipo IN ('PERFIL_TR069', 'VLAN_GESTION'));
--rollback ALTER TABLE lotes DROP COLUMN tipo;
