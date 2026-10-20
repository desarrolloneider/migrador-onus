package com.guajiranet.migradoronu.conciliacion.infraestructura.entrada.programacion;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.guajiranet.migradoronu.conciliacion.infraestructura.salida.smartolt.SmartOltInventoryAdapter;

/**
 * Solo en el proceso web: descarga el inventario completo de SmartOLT al arrancar y lo renueva cada 15 minutos. Así
 * "Estado de OLT", las ONUs de cada OLT y las vistas previas salen de la misma copia, y se gastan 4 descargas por hora sin
 * importar cuánta gente esté mirando (SmartOLT permite 15 y bloquea al pasarse; antes, cada pestaña abierta en "Estado
 * de OLT" gastaba unas 6 por hora y el cupo se acababa: 20 de octubre). Cada cuánto: MIGRADOR_PRECARGA_SMARTOLT_MINUTOS
 * (0 la apaga, por ejemplo en una copia de pruebas que comparte el token de SmartOLT con producción).
 */
@Component
@ConditionalOnWebApplication
public class PrecargaDeSmartOlt implements DisposableBean {

    private static final Logger LOG = LoggerFactory.getLogger(PrecargaDeSmartOlt.class);

    private final SmartOltInventoryAdapter smartOlt;
    private final long minutos;
    private final ExecutorService hilo = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "precarga-smartolt");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean enCurso = new AtomicBoolean();
    private volatile long ultimaEn;

    public PrecargaDeSmartOlt(SmartOltInventoryAdapter smartOlt,
                              @org.springframework.beans.factory.annotation.Value("${MIGRADOR_PRECARGA_SMARTOLT_MINUTOS:15}") long minutos) {
        this.smartOlt = smartOlt;
        this.minutos = minutos;
    }

    /** Se revisa cada minuto; descarga cuando pasaron los minutos configurados desde la última (y al arrancar). */
    @Scheduled(initialDelay = 30_000, fixedDelay = 60_000)
    public void renovar() {
        if (minutos <= 0 || System.currentTimeMillis() - ultimaEn < minutos * 60_000 || !enCurso.compareAndSet(false, true)) {
            return;
        }
        hilo.submit(() -> {
            long inicio = System.currentTimeMillis();
            try {
                if (smartOlt.refrescarInventario()) {
                    ultimaEn = inicio;
                    LOG.info("Copia del inventario de SmartOLT renovada en {} s", (System.currentTimeMillis() - inicio) / 1000);
                } else {
                    LOG.warn("No se renovó la copia de SmartOLT: no quedaba cupo de descargas en esta hora. Se reintenta en un minuto.");
                }
            } catch (RuntimeException e) {
                // Un fallo no deja la precarga parada: se vuelve a intentar en 5 minutos.
                ultimaEn = inicio - Math.max(0, minutos - 5) * 60_000;
                LOG.warn("No se pudo renovar la copia de SmartOLT: {}. Se sigue usando la anterior.", e.getMessage());
            } finally {
                enCurso.set(false);
            }
        });
    }

    @Override
    public void destroy() {
        hilo.shutdownNow();
    }
}
