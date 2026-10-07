package com.guajiranet.migradoronu.ensamblaje.basedatos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Aplica el changelog maestro completo sobre PostgreSQL 17 real y comprueba las reglas que viven en la base
 * (arquitectura 12 y 13.1). Se salta si Docker no está corriendo.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = EsquemaBaseDatosTest.Aplicacion.class)
class EsquemaBaseDatosTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class Aplicacion {
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void creaLasOnceTablasDeLaArquitectura() {
        List<String> tables = jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name NOT LIKE 'databasechangelog%'
                """, String.class);

        assertThat(tables).containsExactlyInAnyOrder("usuarios", "olts", "lotes", "lote_items", "ejecuciones_onu",
                "pasos_ejecucion", "historial_onus", "auditoria_eventos", "parametros", "claves_idempotencia",
                "procesos_ejecutores");
    }

    @Test
    void laAuditoriaAceptaInsercionesPeroNoCambiosNiBorrados() {
        jdbc.update("INSERT INTO auditoria_eventos (actor_tipo, accion, hash) VALUES ('CONSOLA', 'INTEGRIDAD_VERIFICADA', ?)",
                "a".repeat(64));

        // Spring 7 no copia el texto de PostgreSQL en el mensaje de la excepción: se revisa la causa raíz.
        assertThatThrownBy(() -> jdbc.update("UPDATE auditoria_eventos SET mensaje = 'alterado'"))
                .isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("solo inserción: UPDATE");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM auditoria_eventos"))
                .isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("solo inserción: DELETE");
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE auditoria_eventos"))
                .isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("solo inserción: TRUNCATE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria_eventos WHERE hash = ?", Long.class, "a".repeat(64)))
                .isEqualTo(1);
    }

    @Test
    void unaOnuNoPuedeEstarEnDosLotesActivosALaVez() {
        long usuario = insertUser("rn16");
        long olt = insertOlt("RN16");
        long primerItem = insertItem(insertBatch(usuario, 1601), olt, "HWTC00001601");
        long segundoItem = insertItem(insertBatch(usuario, 1602), olt, "HWTC00001601");
        long primeraEjecucion = insertRun(primerItem, "HWTC00001601");

        assertThatThrownBy(() -> insertRun(segundoItem, "HWTC00001601"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause().hasMessageContaining("uk_ejecuciones_onu_sn_activa");

        jdbc.update("UPDATE ejecuciones_onu SET estado = 'COMPLETADA' WHERE id = ?", primeraEjecucion);
        assertThat(insertRun(segundoItem, "HWTC00001601")).isPositive();
    }

    @Test
    void unaExcluidaSinMotivoNoSeGuarda() {
        long lote = insertBatch(insertUser("rn04"), 401);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO lote_items (lote_id, sn, grupo, olt_codigo, orden) VALUES (?, 'HWTC00000401', 'EXCLUIDA', 'X', 0)
                """, lote))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause().hasMessageContaining("ck_lote_items_motivo");
    }

    @Test
    void unaFallidaSoloSeRevisaConNotaDeDiezCaracteres() {
        long usuario = insertUser("rn26");
        long run = insertRun(insertItem(insertBatch(usuario, 2601), insertOlt("RN26"), "HWTC00002601"), "HWTC00002601");
        jdbc.update("""
                UPDATE ejecuciones_onu SET estado = 'FALLIDA', paso_actual = 'P06', codigo_fallo = 'FALLO_SIN_PERFIL',
                       mensaje_fallo = 'sin perfil' WHERE id = ?
                """, run);

        assertThatThrownBy(() -> jdbc.update("""
                UPDATE ejecuciones_onu SET revisada = TRUE, revisada_por = ?, revisada_en = now(), nota_revision = 'ok' WHERE id = ?
                """, usuario, run))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause().hasMessageContaining("ck_ejecuciones_onu_revision");
    }

    @Test
    void losParametrosInicialesDejanSinValorElGuardadoDeLaOlt() {
        assertThat(jdbc.queryForObject("SELECT valor IS NULL FROM parametros WHERE clave = 'guardar_configuracion_olt'", Boolean.class))
                .isTrue();
        assertThat(jdbc.queryForObject("SELECT valor #>> '{}' FROM parametros WHERE clave = 'perfil_tr069'", String.class))
                .isEqualTo("guajiranet1");
        assertThat(jdbc.queryForObject("SELECT valor::int FROM parametros WHERE clave = 'tamano_maximo_lote'", Integer.class))
                .isEqualTo(50);
    }

    private long insertUser(String usuario) {
        return jdbc.queryForObject("INSERT INTO usuarios (usuario, nombre, hash_clave) VALUES (?, ?, 'x') RETURNING id",
                Long.class, usuario, "Prueba " + usuario);
    }

    private long insertOlt(String codigo) {
        return jdbc.queryForObject("""
                INSERT INTO olts (codigo, nombre, direccion, puerto, protocolo, tarjetas_permitidas)
                VALUES (?, ?, '10.0.0.1', 22, 'SSH', '["0/1"]') RETURNING id
                """, Long.class, codigo, "OLT " + codigo);
    }

    private long insertBatch(long usuario, int consecutivo) {
        return jdbc.queryForObject("""
                INSERT INTO lotes (numero, modo, marca, estado, creado_por, vence_en, confirmado_por, confirmado_en,
                                   parametros_usados, procedimiento_version)
                VALUES (?, 'LOTE', 'HUAWEI', 'CONFIRMADO', ?, now() + interval '30 minutes', ?, now(), '{}', 'huawei-cli-1')
                RETURNING id
                """, Long.class, "LOT-2026-%06d".formatted(consecutivo), usuario, usuario);
    }

    private long insertItem(long lote, long olt, String sn) {
        return jdbc.queryForObject("""
                INSERT INTO lote_items (lote_id, sn, ip, grupo, olt_codigo, olt_id, tarjeta, pon, onu_id, orden)
                VALUES (?, ?, '10.20.30.40', 'ENTRA', 'X', ?, '0/1', 0, 33, 0) RETURNING id
                """, Long.class, lote, sn, olt);
    }

    private long insertRun(long item, String sn) {
        return jdbc.queryForObject("""
                INSERT INTO ejecuciones_onu (lote_id, lote_item_id, sn, orden, estado, procedimiento_version)
                SELECT lote_id, id, ?, 0, 'PENDIENTE', 'huawei-cli-1' FROM lote_items WHERE id = ? RETURNING id
                """, Long.class, sn, item);
    }
}
