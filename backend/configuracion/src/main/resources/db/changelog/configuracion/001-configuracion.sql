--liquibase formatted sql

--changeset migrador:configuracion-001-olts
--comment: OLT registradas y sus tarjetas permitidas (RN-14). Sin credenciales: viven en el servidor (arquitectura 11.1).
CREATE TABLE olts (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo              VARCHAR(40)  NOT NULL,
    nombre              VARCHAR(120) NOT NULL,
    direccion           VARCHAR(255),
    puerto              INTEGER CHECK (puerto BETWEEN 1 AND 65535),
    protocolo           VARCHAR(10) CHECK (protocolo IN ('SSH', 'TELNET')),
    tarjetas_permitidas JSONB NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(tarjetas_permitidas) = 'array'),
    activa              BOOLEAN NOT NULL DEFAULT TRUE,
    actualizado_por     BIGINT,
    actualizado_en      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_olts_codigo UNIQUE (codigo)
);
--rollback DROP TABLE olts;

--changeset migrador:configuracion-001-parametros
--comment: Parámetros de la spec 7.5. Un valor NULL significa "sin valor" (por ejemplo, PC-36 sin decidir).
CREATE TABLE parametros (
    clave           VARCHAR(80) PRIMARY KEY,
    valor           JSONB,
    descripcion     TEXT NOT NULL,
    actualizado_por BIGINT,
    actualizado_en  TIMESTAMPTZ NOT NULL DEFAULT now()
);
--rollback DROP TABLE parametros;

--changeset migrador:configuracion-002-parametros-iniciales
--comment: Valores iniciales de la spec 7.5. Los marcados como propuesta se ajustan con el piloto.
INSERT INTO parametros (clave, valor, descripcion) VALUES
    ('perfil_tr069',                         '"guajiranet1"',     'Perfil TR-069 que se asigna (PC-02 cerrado, RN-12)'),
    ('guardar_configuracion_olt',            NULL,                'Si se guarda la configuración de la OLT y cuándo. Sin valor: solo simulación (PC-36, DEC-34)'),
    ('tiempo_conexion_olt_segundos',         '10',                'Tiempo máximo para conectarse a la OLT (propuesta)'),
    ('tiempo_respuesta_comando_segundos',    '30',                'Tiempo máximo de respuesta a un comando (propuesta)'),
    ('tiempo_peticion_api_segundos',         '30',                'Tiempo máximo de cada petición a SmartOLT o Wispro (propuesta)'),
    ('reintentos_lectura',                   '3',                 'Reintentos de una lectura, con espera creciente (propuesta, RN-18)'),
    ('onus_en_paralelo',                     '1',                 'ONUs ejecutándose al mismo tiempo (PC-15)'),
    ('sesiones_por_olt',                     '1',                 'Sesiones de la herramienta por OLT (RN-34)'),
    ('tamano_maximo_lote',                   '50',                'Máximo de ONUs por lote, 200 si el piloto sale bien (DEC-32)'),
    ('vigencia_vista_previa_minutos',        '30',                'Vigencia de una vista previa sin confirmar (DEC-32)'),
    ('inactividad_sesion_minutos',           '30',                'Cierre de la sesión de usuario por inactividad (propuesta)'),
    ('detencion_automatica_fallos_seguidos', '3',                 'ONUs seguidas con FALLO_SISTEMA_NO_RESPONDE que detienen el lote (propuesta, PC-24)'),
    ('intentos_fallidos_bloqueo',            '5',                 'Intentos fallidos seguidos que bloquean al usuario (propuesta)'),
    ('minutos_bloqueo',                      '15',                'Minutos de bloqueo por intentos fallidos (propuesta)'),
    ('zona_horaria',                         '"America/Bogota"',  'Zona horaria en que se muestran las fechas (RN-21)'),
    ('parada_ordenada_minutos',              '5',                 'Espera máxima de la parada ordenada (propuesta, RN-38)'),
    ('tiempo_estimado_por_onu_minutos',      '2',                 'Tiempo estimado máximo por ONU para la vista previa (propuesta)'),
    ('ventana_horaria',                      NULL,                'Ventana horaria para ejecutar. Sin valor: sin restricción (PC-25)');
--rollback DELETE FROM parametros;

--changeset migrador:configuracion-003-olts-smartolt
--comment: Las 8 OLT de SmartOLT (diagnóstico del 6 de octubre). El código es el nombre en SmartOLT. Sin tarjetas permitidas: se escogen en Configuración. Solo OLT-CONEJO tiene acceso por consola, para la prueba con un cliente.
INSERT INTO olts (codigo, nombre, direccion, puerto, protocolo) VALUES
    ('OLT_ALBANIA',        'Albania (MA5608T)',         NULL,           NULL, NULL),
    ('OLT_SAN_JUAN',       'San Juan (MA5683T)',        NULL,           NULL, NULL),
    ('OLT_NEIVA',          'Neiva (MA5608T)',           NULL,           NULL, NULL),
    ('OLT_VILLAREINA',     'Villareina (MA5608T)',      NULL,           NULL, NULL),
    ('OLT_FONSECA',        'Fonseca (MA5800-X17)',      NULL,           NULL, NULL),
    ('OLT_SAN_JUAN_2',     'San Juan 2 (MA5608T)',      NULL,           NULL, NULL),
    ('OLT_ZTE-ZXA10-C650', 'ZTE C650 (sin procedimiento)', NULL,        NULL, NULL),
    ('OLT-CONEJO',         'Conejo (MA5608T)',          '10.11.104.50', 22,   'SSH')
ON CONFLICT (codigo) DO NOTHING;
--rollback DELETE FROM olts WHERE codigo IN ('OLT_ALBANIA','OLT_SAN_JUAN','OLT_NEIVA','OLT_VILLAREINA','OLT_FONSECA','OLT_SAN_JUAN_2','OLT_ZTE-ZXA10-C650','OLT-CONEJO');


--changeset migrador:configuracion-004-huella-ssh
--comment: La huella de la llave SSH de cada OLT, fijada desde Configuración tras probar la conexión. No es secreta. Si el .env trae CONSOLA_<CODIGO>_HUELLA, manda la del .env.
ALTER TABLE olts ADD COLUMN huella_ssh VARCHAR(200);
--rollback ALTER TABLE olts DROP COLUMN huella_ssh;

--changeset migrador:configuracion-005-lotes-de-mil
--comment: Lotes de hasta 1000 ONUs (pedido del 9 de octubre). Medido: 24 s por ONU en promedio, 41 s el percentil 90.
UPDATE parametros SET valor = '1000', actualizado_en = now() WHERE clave = 'tamano_maximo_lote';
UPDATE parametros SET valor = '1', actualizado_en = now() WHERE clave = 'tiempo_estimado_por_onu_minutos';
--rollback UPDATE parametros SET valor = '200' WHERE clave = 'tamano_maximo_lote';

--changeset migrador:configuracion-006-paralelo-y-guardado
--comment: Varias OLT a la vez (una sesión por OLT) y un save cada N ONUs (pedido del 9 de octubre).
INSERT INTO parametros (clave, valor, descripcion) VALUES
    ('olts_en_paralelo',  '6',  'OLT trabajando al mismo tiempo, una sesión por OLT'),
    ('onus_por_guardado', '10', 'Cada cuántas ONUs cambiadas se guarda la configuración de la OLT')
ON CONFLICT (clave) DO NOTHING;
--rollback DELETE FROM parametros WHERE clave IN ('olts_en_paralelo', 'onus_por_guardado');
