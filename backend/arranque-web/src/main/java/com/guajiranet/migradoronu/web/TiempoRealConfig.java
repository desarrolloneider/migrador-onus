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

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registro) {
        registro.addEndpoint("/ws");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registro) {
        registro.enableSimpleBroker("/topic");
    }
}
