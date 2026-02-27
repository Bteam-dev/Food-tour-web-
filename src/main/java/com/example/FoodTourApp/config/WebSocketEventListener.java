package com.example.FoodTourApp.config;

import com.example.FoodTourApp.service.ChatPresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Lắng nghe sự kiện WebSocket disconnect.
 * Khi user mất kết nối (tắt app, mất mạng, logout...) → clear presence state.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final ChatPresenceService chatPresenceService;

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        log.debug("WebSocket session disconnected: {}", sessionId);
        chatPresenceService.sessionDisconnected(sessionId);
    }
}

