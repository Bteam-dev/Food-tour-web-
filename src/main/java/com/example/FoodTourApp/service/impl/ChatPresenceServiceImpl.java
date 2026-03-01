package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.service.ChatPresenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Track user đang active trong conversation nào.
 * Giống Messenger/Zalo: nếu user đang mở màn hình chat → không gửi FCM push.
 *
 * Lưu in-memory (Map), đủ dùng cho single-node.
 * Nếu scale multi-node thì thay bằng Redis Pub/Sub.
 *
 * Lifecycle:
 *  - Client gửi WS /app/chat.active   { conversationId }  khi vào màn hình chat
 *  - Client gửi WS /app/chat.inactive { conversationId }  khi ra khỏi màn hình chat
 *  - Khi WebSocket disconnect, toàn bộ state của session bị clear tự động
 *    (xử lý ở WebSocketEventListener)
 */
@Service
@Slf4j
public class ChatPresenceServiceImpl implements ChatPresenceService {

    /**
     * userId → Set<conversationId> đang active
     */
    private final Map<Integer, Set<Long>> activeMap = new ConcurrentHashMap<>();

    /**
     * sessionId → userId  (để clear khi disconnect)
     */
    private final Map<String, Integer> sessionUserMap = new ConcurrentHashMap<>();

    @Override
    public void userEnterConversation(Integer userId, Long conversationId, String sessionId) {
        activeMap.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(conversationId);
        if (sessionId != null) sessionUserMap.put(sessionId, userId);
        log.debug("User {} entered conversation {}", userId, conversationId);
    }

    @Override
    public void userLeaveConversation(Integer userId, Long conversationId) {
        Set<Long> convs = activeMap.get(userId);
        if (convs != null) {
            convs.remove(conversationId);
            if (convs.isEmpty()) activeMap.remove(userId);
        }
        log.debug("User {} left conversation {}", userId, conversationId);
    }

    @Override
    public void sessionDisconnected(String sessionId) {
        Integer userId = sessionUserMap.remove(sessionId);
        if (userId != null) {
            activeMap.remove(userId);
            log.debug("Session {} disconnected – cleared presence for user {}", sessionId, userId);
        }
    }

    @Override
    public boolean isUserActiveInConversation(Integer userId, Long conversationId) {
        Set<Long> convs = activeMap.get(userId);
        return convs != null && convs.contains(conversationId);
    }

    @Override
    public Set<Long> getActiveConversations(Integer userId) {
        return Collections.unmodifiableSet(
                activeMap.getOrDefault(userId, Collections.emptySet()));
    }
}

