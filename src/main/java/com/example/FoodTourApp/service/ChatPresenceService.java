package com.example.FoodTourApp.service;

import java.util.Set;

/**
 * Track user đang active trong conversation nào.
 * Giống Messenger/Zalo: nếu user đang mở màn hình chat → không gửi FCM push.
 */
public interface ChatPresenceService {

    void userEnterConversation(Integer userId, Long conversationId, String sessionId);

    void userLeaveConversation(Integer userId, Long conversationId);

    /**
     * Gọi khi WebSocket session disconnect – xoá toàn bộ state của session đó.
     */
    void sessionDisconnected(String sessionId);

    /**
     * Kiểm tra user có đang mở màn hình conversation đó không.
     * → Nếu true: KHÔNG gửi FCM push.
     * → Nếu false: gửi FCM push để báo tin nhắn mới.
     */
    boolean isUserActiveInConversation(Integer userId, Long conversationId);

    /** Lấy tất cả conversation mà user đang active (debug/testing) */
    Set<Long> getActiveConversations(Integer userId);
}
