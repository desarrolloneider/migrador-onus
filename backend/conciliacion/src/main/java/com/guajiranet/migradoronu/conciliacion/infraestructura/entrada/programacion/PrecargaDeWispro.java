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

import com.guajiranet.migradoronu.conciliacion.infraestructura.salida.wispro.WisproDirectoryAdapter;

/**
 * Solo en el proceso web: descarga Wispro al arrancar y la renueva cada 20 minutos, para que armar una vista previa no
 * tenga que esperar los 2 minutos de la descarga completa. La descarga corre en un hilo propio: el programador es el
 * mismo que reparte los avisos en vivo y no se debe bloquear.
 */
@Component
@ConditionalOnWebApplication
public class PrecargaDeWispro implements DisposableBean {

    private static final Logger LOG = LoggerFactory.getLogger(PrecargaDeWispro.class);

    private final WisproDirectoryAdapter wispro;
    private final ExecutorService hilo = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "precarga-wispro");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean enCurso = new AtomicBoolean();

    public PrecargaDeWispro(WisproDirectoryAdapter wispro) {
        this.wispro = wispro;
    }

    @Scheduled(initialDelay = 15_000, fixedDelay = 20 * 60_000)
    public void renovar() {
        if (!enCurso.compareAndSet(false, true)) {
            return;
        }
        hilo.submit(() -> {
            long inicio = System.currentTimeMillis();
            try {
                wispro.refrescar();
                LOG.info("Copia de Wispro renovada en {} s", (System.currentTimeMillis() - inicio) / 1000);
            } catch (RuntimeException e) {
                LOG.warn("No se pudo renovar la copia de Wispro: {}. Se sigue usando la anterior mientras esté vigente.", e.getMessage());
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
