-- Deja la Configuración del servidor igual a la que se usa hoy (15 de octubre de 2026).
-- Solo cambia valores existentes; no toca usuarios, lotes ni OLT. Correr una vez:
--   docker exec -i migrador-onu-bd psql -U postgres -d migrador < parametros-servidor.sql
UPDATE parametros SET valor = '"GUAJIRANET1"', actualizado_en = now() WHERE clave = 'perfil_tr069';
UPDATE parametros SET valor = '"POR_ONU"',     actualizado_en = now() WHERE clave = 'guardar_configuracion_olt';
UPDATE parametros SET valor = '1000',          actualizado_en = now() WHERE clave = 'tamano_maximo_lote';
UPDATE parametros SET valor = '240',           actualizado_en = now() WHERE clave = 'vigencia_vista_previa_minutos';
UPDATE parametros SET valor = '1',             actualizado_en = now() WHERE clave = 'tiempo_estimado_por_onu_minutos';
UPDATE parametros SET valor = '6',             actualizado_en = now() WHERE clave = 'olts_en_paralelo';
UPDATE parametros SET valor = '10',            actualizado_en = now() WHERE clave = 'onus_por_guardado';
UPDATE parametros SET valor = '1',             actualizado_en = now() WHERE clave = 'detencion_automatica_fallos_seguidos';
SELECT clave, valor FROM parametros
WHERE clave IN ('perfil_tr069', 'guardar_configuracion_olt', 'tamano_maximo_lote', 'vigencia_vista_previa_minutos',
                'tiempo_estimado_por_onu_minutos', 'olts_en_paralelo', 'onus_por_guardado', 'detencion_automatica_fallos_seguidos')
ORDER BY clave;
