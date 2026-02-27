package com.example.FoodTourApp.controller.UserController;

import com.example.FoodTourApp.DTO.ChatDTO.SendMessageRequest;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.service.ChatPresenceService;
import com.example.FoodTourApp.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class WebSocketChatController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatPresenceService chatPresenceService;

    /**
     * Nhận tin nhắn TEXT từ client qua WebSocket và lưu + broadcast.
     * Client gửi đến: /app/chat.send
     *
     * NOTE: File message KHÔNG đi qua đây — file được upload qua REST
     * POST /api/user/chat/conversations/{id}/upload, service sẽ tự broadcast.
     */
    @MessageMapping("/chat.send")
    public void sendMessage(@Payload SendMessageRequest request, Principal principal) {
        log.info("WS sendMessage – conversationId={}, type={}", request.getConversationId(), request.getMessageType());

        if (principal == null) {
            log.error("WebSocket principal is NULL – authentication failed");
            return;
        }

        User user = extractUser(principal);
        if (user == null) {
            log.error("Could not extract User from principal type: {}", principal.getClass().getName());
            return;
        }

        try {
            // chatService.sendMessage tự broadcast /topic/conversation/{id} và /user/queue/messages
            chatService.sendMessage(user.getId(), request);
            log.info("WS message saved & broadcast – conversation {}", request.getConversationId());
        } catch (Exception e) {
            log.error("WS sendMessage error: {}", e.getMessage(), e);
            messagingTemplate.convertAndSendToUser(
                    user.getId().toString(),
                    "/queue/errors",
                    "Error: " + e.getMessage()
            );
        }
    }

    /**
     * Client báo vào màn hình conversation → không gửi FCM push cho user này.
     * Gửi đến: /app/chat.active
     * Payload: { "conversationId": 123 }
     *
     * Giống Messenger/Zalo: khi đang mở chat → server biết → bỏ qua FCM push
     */
    @MessageMapping("/chat.active")
    public void markChatActive(@Payload Map<String, Long> payload,
                               Principal principal,
                               SimpMessageHeaderAccessor headerAccessor) {
        User user = extractUser(principal);
        if (user == null) return;

        Long conversationId = payload.get("conversationId");
        if (conversationId == null) return;

        String sessionId = headerAccessor.getSessionId();
        chatPresenceService.userEnterConversation(user.getId(), conversationId, sessionId);
        log.debug("User {} marked active in conversation {}", user.getId(), conversationId);
    }

    /**
     * Client báo ra khỏi màn hình conversation → cho phép gửi FCM push lại.
     * Gửi đến: /app/chat.inactive
     * Payload: { "conversationId": 123 }
     */
    @MessageMapping("/chat.inactive")
    public void markChatInactive(@Payload Map<String, Long> payload, Principal principal) {
        User user = extractUser(principal);
        if (user == null) return;

        Long conversationId = payload.get("conversationId");
        if (conversationId == null) return;

        chatPresenceService.userLeaveConversation(user.getId(), conversationId);
        log.debug("User {} marked inactive in conversation {}", user.getId(), conversationId);
    }

    private User extractUser(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken auth) {
            if (auth.getPrincipal() instanceof User u) {
                return u;
            }
        }
        return null;
    }
}
