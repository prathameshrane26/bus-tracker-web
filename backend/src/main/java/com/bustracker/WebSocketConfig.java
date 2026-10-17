package com.bustracker;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor headers = StompHeaderAccessor.wrap(message);
                System.out.println("[WebSocket] Inbound Message: command=" + headers.getCommand() 
                        + ", destination=" + headers.getDestination() 
                        + ", session=" + headers.getSessionId());
                return message;
            }
        });
    }

    @EventListener
    public void onSocketConnect(SessionConnectEvent event) {
        StompHeaderAccessor headers = StompHeaderAccessor.wrap(event.getMessage());
        System.out.println("[WebSocket] CONNECT received (session=" + headers.getSessionId() + ")");
    }

    @EventListener
    public void onSocketConnected(SessionConnectedEvent event) {
        StompHeaderAccessor headers = StompHeaderAccessor.wrap(event.getMessage());
        System.out.println("[WebSocket] CONNECTED (session=" + headers.getSessionId() + ")");
    }

    @EventListener
    public void onSocketSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor headers = StompHeaderAccessor.wrap(event.getMessage());
        System.out.println("[WebSocket] SUBSCRIBE to " + headers.getDestination() 
                + " (session=" + headers.getSessionId() + ")");
    }

    @EventListener
    public void onSocketDisconnected(SessionDisconnectEvent event) {
        System.out.println("[WebSocket] DISCONNECTED (session=" + event.getSessionId()
                + ", status=" + event.getCloseStatus() + ")");
    }
}