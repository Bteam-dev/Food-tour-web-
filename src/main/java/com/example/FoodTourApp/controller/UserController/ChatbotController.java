package com.example.FoodTourApp.controller.UserController;

import com.example.FoodTourApp.DTO.ChatbotDTO.*;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.service.ChatbotConversationService;
import com.example.FoodTourApp.service.ChatbotMessageService;
import com.example.FoodTourApp.service.impl.ChatbotAIServiceImpl;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user/chatbot")
@RequiredArgsConstructor
public class ChatbotController {

    private static final Logger logger = LoggerFactory.getLogger(ChatbotController.class);

    private final ChatbotConversationService conversationService;
    private final ChatbotMessageService messageService;
    private final ChatbotAIServiceImpl chatbotAIService;

    /**
     * Tạo conversation mới
     * POST /api/chatbot/conversations
     */
    @PostMapping("/conversations")
    public ResponseEntity<?> createConversation(
            @Valid @RequestBody CreateChatbotConversationRequest request,
            @AuthenticationPrincipal User user) {

        logger.info("User ID {} is creating new chatbot conversation with title: {}",
                user.getId(), request.getTitle());

        try {
            ChatbotConversationResponse conv = conversationService.createConversation(user, request);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Conversation created successfully");
            result.put("data", conv);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error creating conversation for user ID {}: {}", user.getId(), e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Lấy list conversation của user
     * GET /api/chatbot/conversations
     */
    @GetMapping("/conversations")
    public ResponseEntity<?> getUserConversations(@AuthenticationPrincipal User user) {
        logger.info("User ID {} is getting all chatbot conversations", user.getId());

        try {
            List<ChatbotConversationResponse> conversations = conversationService.getUserConversations(user);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Conversations retrieved successfully");
            result.put("data", conversations);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error getting conversations for user ID {}: {}", user.getId(), e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Đổi tên conversation
     * PUT /api/chatbot/conversations/{id}
     */
    @PutMapping("/conversations/{id}")
    public ResponseEntity<?> renameConversation(
            @PathVariable Long id,
            @Valid @RequestBody RenameChatbotConversationRequest request,
            @AuthenticationPrincipal User user) {

        logger.info("User ID {} is renaming conversation {} to: {}", user.getId(), id, request.getTitle());

        try {
            ChatbotConversationResponse updated = conversationService.renameConversation(id, request, user);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Conversation renamed successfully");
            result.put("data", updated);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error renaming conversation {} for user ID {}: {}", id, user.getId(), e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Xóa conversation
     * DELETE /api/chatbot/conversations/{id}
     */
    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<?> deleteConversation(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {

        logger.info("User ID {} is deleting conversation {}", user.getId(), id);

        try {
            conversationService.deleteConversation(id, user);
            // Xóa AI memory của conversation này để giải phóng RAM
            chatbotAIService.removeConversationMemory(id);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Conversation deleted successfully");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error deleting conversation {} for user ID {}: {}", id, user.getId(), e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Gửi message mới (user hỏi → bot trả lời)
     * POST /api/user/chatbot/{conversationId}/messages
     */
    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<?> sendMessage(
            @PathVariable Long conversationId,
            @Valid @RequestBody SendChatbotMessageRequest request,
            @AuthenticationPrincipal User user) {

        logger.info("User ID {} is sending message to conversation {}: {}",
                user.getId(), conversationId, request.getContent());

        try {
            ChatbotMessageResponse reply = messageService.sendMessage(user, conversationId, request);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Message sent and replied successfully");
            result.put("data", reply);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error sending message for user ID {} in conversation {}: {}",
                    user.getId(), conversationId, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Lấy lịch sử messages trong 1 conversation
     * GET /api/user/chatbot/conversations/{id}/messages
     */
    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<?> getConversationMessages(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {

        logger.info("User ID {} is getting messages from conversation {}", user.getId(), id);

        try {
            List<ChatbotMessageResponse> messages = messageService.getConversationMessages(id, user);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Messages retrieved successfully");
            result.put("data", messages);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error getting messages for conversation {} by user ID {}: {}",
                    id, user.getId(), e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}