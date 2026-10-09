package com.guajiranet.migradoronu.worker;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.guajiranet.migradoronu.comun.puertos.ActorType;
import com.guajiranet.migradoronu.comun.puertos.AuditAction;
import com.guajiranet.migradoronu.comun.puertos.AuditEntry;
import com.guajiranet.migradoronu.comun.puertos.AuditTrailPort;
import com.guajiranet.migradoronu.configuracion.puertos.entrada.QueryParametersPort;
import com.guajiranet.migradoronu.ejecucion.aplicacion.EjecutorDeOnus;
import com.guajiranet.migradoronu.ejecucion.puertos.salida.ColaDeEjecucionPort;

/**
 * El ciclo del proceso ejecutor: al arrancar recupera lo interrumpido y se registra; cada 3 segundos reparte la cola
 * y cada 10 segundos late (arquitectura 9.1). Un solo proceso ejecutor (RN-40) con un hilo por OLT: cada OLT tiene
 * una sola sesión (RN-34) y hasta {@code olts_en_paralelo} OLT trabajan a la vez.
 */
@Component
public class EjecutorProgramado {

    private static final Logger LOG = LoggerFactory.getLogger(EjecutorProgramado.class);

    private final EjecutorDeOnus ejecutor;
    private final ColaDeEjecucionPort cola;
    private final AuditTrailPort auditoria;
    private final QueryParametersPort parametros;
    private final ExecutorService hilos = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setName("ejecutor-olt");
        return t;
    });
    /** Las OLT que tienen un hilo trabajando ahora. */
    private final Set<String> trabajando = ConcurrentHashMap.newKeySet();
    private volatile Long procesoId;
    private volatile boolean deteniendo;

    public EjecutorProgramado(EjecutorDeOnus ejecutor, ColaDeEjecucionPort cola, AuditTrailPort auditoria, QueryParametersPort parametros) {
        this.ejecutor = ejecutor;
        this.cola = cola;
        this.auditoria = auditoria;
        this.parametros = parametros;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void arrancar() {
        int interrumpidas = cola.recuperarInterrumpidas();
        procesoId = cola.registrarProceso(host(), ProcessHandle.current().pid());
        auditoria.record(AuditEntry.builder(ActorType.PROCESO_EJECUTOR, AuditAction.EJECUTOR_INICIADO).sistema("ejecutor")
                .peticion(Map.of("proceso", procesoId, "interrumpidas", interrumpidas)).resultado("OK")
                .mensaje("Proceso ejecutor iniciado"
                        + (interrumpidas > 0 ? "; " + interrumpidas + " ONUs interrumpidas quedaron como fallidas." : "."))
                .build());
        LOG.info("Proceso ejecutor {} listo", procesoId);
    }

    @Scheduled(fixedDelay = 3_000, initialDelay = 5_000)
    public void atenderCola() {
        if (procesoId == null || deteniendo) {
            return;
        }
        try {
            cola.atenderDetenciones();
            int maximo = entero("olts_en_paralelo", 1, 1, 8);
            for (String olt : cola.oltsConPendientes()) {
                if (trabajando.size() >= maximo) {
                    break;
                }
                if (trabajando.add(olt)) {
                    hilos.submit(() -> trabajar(olt));
                }
            }
        } catch (RuntimeException e) {
            LOG.error("Error atendiendo la cola", e);
        }
    }

    /** Un hilo por OLT: hace sus ONUs una tras otra con la misma sesión hasta que no queden. */
    private void trabajar(String olt) {
        Thread.currentThread().setName("ejecutor-" + olt);
        EjecutorDeOnus.SesionOlt sesion = ejecutor.nuevaSesion(olt);
        try {
            while (!deteniendo && ejecutor.ejecutarSiguiente(procesoId, sesion, entero("onus_por_guardado", 1, 1, 50))) {
                cola.latido(procesoId);
            }
        } catch (RuntimeException e) {
            LOG.error("Error ejecutando ONUs de {}", olt, e);
        } finally {
            try {
                ejecutor.cerrar(sesion);
            } catch (RuntimeException e) {
                LOG.error("Error cerrando la sesión de {}", olt, e);
            }
            trabajando.remove(olt);
        }
    }

    private int entero(String clave, int porDefecto, int minimo, int maximo) {
        try {
            Object valor = parametros.currentParameters().valores().get(clave);
            int n = valor instanceof Number num ? num.intValue() : valor == null ? porDefecto : Integer.parseInt(valor.toString().strip());
            return Math.max(minimo, Math.min(maximo, n));
        } catch (RuntimeException e) {
            return porDefecto;
        }
    }

    @Scheduled(fixedRate = 10_000, initialDelay = 5_000)
    public void latir() {
        if (procesoId != null) {
            cola.latido(procesoId);
        }
    }

    /** Parada ordenada (RN-38): no toma ONUs nuevas; las que están en curso terminan y se guarda lo pendiente. */
    @PreDestroy
    public void detener() {
        deteniendo = true;
        hilos.shutdown();
        try {
            if (!hilos.awaitTermination(entero("parada_ordenada_minutos", 5, 1, 30), TimeUnit.MINUTES)) {
                LOG.warn("Parada ordenada: quedaron ONUs en curso; al arrancar quedarán como interrumpidas.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (procesoId != null) {
            cola.detenerProceso(procesoId);
            auditoria.record(AuditEntry.builder(ActorType.PROCESO_EJECUTOR, AuditAction.EJECUTOR_DETENIDO).sistema("ejecutor")
                    .peticion(Map.of("proceso", procesoId)).resultado("OK").mensaje("Proceso ejecutor detenido.").build());
        }
    }

    private static String host() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "desconocido";
        }
    }
}
