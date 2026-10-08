package com.guajiranet.migradoronu.worker;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

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
import com.guajiranet.migradoronu.ejecucion.aplicacion.EjecutorDeOnus;
import com.guajiranet.migradoronu.ejecucion.puertos.salida.ColaDeEjecucionPort;

/**
 * El ciclo del proceso ejecutor: al arrancar recupera lo interrumpido y se registra; cada 3 segundos atiende la cola y
 * cada 10 segundos late (arquitectura 9.1). Un solo ejecutor (RN-40).
 */
@Component
public class EjecutorProgramado {

    private static final Logger LOG = LoggerFactory.getLogger(EjecutorProgramado.class);

    private final EjecutorDeOnus ejecutor;
    private final ColaDeEjecucionPort cola;
    private final AuditTrailPort auditoria;
    private volatile Long procesoId;
    private volatile boolean deteniendo;

    public EjecutorProgramado(EjecutorDeOnus ejecutor, ColaDeEjecucionPort cola, AuditTrailPort auditoria) {
        this.ejecutor = ejecutor;
        this.cola = cola;
        this.auditoria = auditoria;
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
            while (!deteniendo && ejecutor.ejecutarSiguiente(procesoId)) {
                cola.latido(procesoId);
            }
        } catch (RuntimeException e) {
            LOG.error("Error atendiendo la cola", e);
        }
    }

    @Scheduled(fixedRate = 10_000, initialDelay = 5_000)
    public void latir() {
        if (procesoId != null) {
            cola.latido(procesoId);
        }
    }

    /** Parada ordenada (RN-38): no toma ONUs nuevas; la que está en curso termina. */
    @PreDestroy
    public void detener() {
        deteniendo = true;
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
