package com.guajiranet.migradoronu.auditoria.infraestructura.salida.persistencia;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.guajiranet.migradoronu.auditoria.dominio.AuditFixtures;
import com.guajiranet.migradoronu.auditoria.dominio.HashChain;
import com.guajiranet.migradoronu.comun.puertos.AuditUnavailableException;

/** RN-30: si la base no responde, quien registra recibe AuditUnavailableException y no actúa. */
class BaseNoDisponibleTest {

    @Test
    void sinBaseDeDatosElRegistroFallaConAuditoriaNoDisponible() {
        DriverManagerDataSource unreachable = new DriverManagerDataSource(
                "jdbc:postgresql://127.0.0.1:1/migrador?connectTimeout=2", "nadie", "nada");
        JdbcAuditRepositoryAdapter adapter =
                new JdbcAuditRepositoryAdapter(unreachable, new DataSourceTransactionManager(unreachable));

        assertThatThrownBy(() -> adapter.append(previous -> HashChain.seal(previous, AuditFixtures.event("x"))))
                .isInstanceOf(AuditUnavailableException.class)
                .hasMessageContaining("RN-30");
    }
}
