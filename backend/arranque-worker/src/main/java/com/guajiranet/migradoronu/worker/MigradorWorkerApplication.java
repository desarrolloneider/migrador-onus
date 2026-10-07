package com.guajiranet.migradoronu.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Proceso ejecutor (arquitectura 9.1). Solo arma las piezas; no tiene lógica. Una pantalla colgada nunca detiene un
 * lote y reiniciar la web no interrumpe ejecuciones.
 *
 * <p>Igual que en la web, cada módulo se agrega al escaneo cuando tenga sus casos de uso (docs/adr/0002).
 */
@EnableScheduling
@SpringBootApplication(scanBasePackages = {
        "com.guajiranet.migradoronu.worker",
        "com.guajiranet.migradoronu.comun",
        "com.guajiranet.migradoronu.auditoria",
        "com.guajiranet.migradoronu.ensamblaje.transversal"
})
public class MigradorWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MigradorWorkerApplication.class, args);
    }
}
