package com.guajiranet.migradoronu.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Avisos en vivo (ESP-08): STOMP sobre WebSocket en {@code /ws}, con los canales en {@code /topic/<canal>}. El
 * saludo HTTP exige sesión iniciada como el resto de la API. Las pantallas solo escuchan; nunca mandan mensajes.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSocketMessageBroker
public class TiempoRealConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Por defecto el saludo solo se acepta del mismo origen. Detrás de un nginx con TLS el backend ve http y el navegador
     * manda {@code Origin: https://...}: no coinciden y la pantalla queda en "Sin conexión en vivo". Con
     * MIGRADOR_ORIGENES_WS (separados por coma, por ejemplo {@code https://migrador.guajiranet.com}) se aceptan esos.
     */
    private final String[] origenes;

    public TiempoRealConfig(@org.springframework.beans.factory.annotation.Value("${MIGRADOR_ORIGENES_WS:}") String origenes) {
        this.origenes = java.util.Arrays.stream(origenes.split(",")).map(String::strip).filter(o -> !o.isEmpty()).toArray(String[]::new);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registro) {
        var punto = registro.addEndpoint("/ws");
        if (origenes.length > 0) {
            punto.setAllowedOriginPatterns(origenes);
        }
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registro) {
        registro.enableSimpleBroker("/topic");
    }
}
