package com.example.FoodTourApp.controller.UserController;

import com.example.FoodTourApp.DTO.ChatDTO.MessageResponse;
import com.example.FoodTourApp.DTO.ChatDTO.SendMessageRequest;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
@Slf4j
public class WebSocketChatController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Nhận tin nhắn từ client qua WebSocket và gửi đến người nhận
     * Client gửi đến: /app/chat.send
     */
    @MessageMapping("/chat.send")
    public void sendMessage(@Payload SendMessageRequest request,
                           Principal principal) {
        log.info("=== WebSocket SEND Message START ===");
        log.info("Request: conversationId={}, messageType={}", request.getConversationId(), request.getMessageType());
        log.info("Principal: {}", principal);

        if (principal == null) {
            log.error("Principal is NULL! Authentication failed in WebSocket");
            return;
        }

        // Extract User from Principal
        User user = null;
        if (principal instanceof UsernamePasswordAuthenticationToken) {
            UsernamePasswordAuthenticationToken auth = (UsernamePasswordAuthenticationToken) principal;
            if (auth.getPrincipal() instanceof User) {
                user = (User) auth.getPrincipal();
            }
        }

        if (user == null) {
            log.error("Could not extract User from Principal! Principal type: {}", principal.getClass().getName());
            return;
        }

        log.info("User ID: {}, Email: {}", user.getId(), user.getEmail());

        try {
            // Lưu message và gửi notification
            MessageResponse response = chatService.sendMessage(user.getId(), request);

            // Broadcast tin nhắn đến conversation topic
            messagingTemplate.convertAndSend(
                    "/topic/conversation/" + request.getConversationId(),
                    response
            );

            log.info("Message sent successfully to conversation {}", request.getConversationId());
        } catch (Exception e) {
            log.error("Error sending message: {}", e.getMessage(), e);
            // Gửi error message về client
            messagingTemplate.convertAndSendToUser(
                    user.getId().toString(),
                    "/queue/errors",
                    "Error: " + e.getMessage()
            );
        }
    }
}
