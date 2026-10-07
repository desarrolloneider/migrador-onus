package com.guajiranet.migradoronu.web;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.guajiranet.migradoronu.comun.puertos.LiveEvent;
import com.guajiranet.migradoronu.comun.puertos.LiveEventsPublisherPort;

/**
 * Reparte los avisos del proceso web a las pantallas conectadas. Los avisos del proceso ejecutor llegarán por
 * NOTIFY de PostgreSQL cuando exista el ejecutor (arquitectura 7 y 8).
 */
@Component
public class StompLiveEventsAdapter implements LiveEventsPublisherPort {

    private final SimpMessagingTemplate mensajes;

    public StompLiveEventsAdapter(SimpMessagingTemplate mensajes) {
        this.mensajes = mensajes;
    }

    @Override
    public void publish(LiveEvent evento) {
        mensajes.convertAndSend("/topic/" + evento.canal(), evento);
    }
}
