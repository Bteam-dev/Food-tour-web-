package com.example.FoodTourApp.controller.UserController;

import com.example.FoodTourApp.DTO.ChatDTO.*;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.service.ChatService;
import com.example.FoodTourApp.service.impl.FileStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user/chat")
@PreAuthorize("hasAnyRole('USER', 'SELLER', 'RESELLER', 'ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatService chatService;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;

    /**
     * Tạo hoặc lấy conversation với user khác
     * POST /api/user/chat/conversations
     */
    @PostMapping("/conversations")
    public ResponseEntity<?> createOrGetConversation(
            @Valid @RequestBody CreateConversationRequest request,
            @AuthenticationPrincipal User user) {
        log.info("User {} creating/getting conversation with user {}", user.getId(), request.getOtherUserId());

        try {
            ConversationResponse response = chatService.createOrGetConversation(user.getId(), request.getOtherUserId());

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Cuộc trò chuyện đã sẵn sàng");
            result.put("data", response);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error creating conversation: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Lấy danh sách tất cả conversations của user
     * GET /api/user/chat/conversations
     */
    @GetMapping("/conversations")
    public ResponseEntity<?> getUserConversations(@AuthenticationPrincipal User user) {
        log.info("User {} retrieving conversations", user.getId());

        try {
            List<ConversationResponse> conversations = chatService.getUserConversations(user.getId());

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", conversations);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error retrieving conversations: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Lấy lịch sử tin nhắn trong conversation
     * GET /api/user/chat/conversations/{conversationId}/messages
     */
    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<?> getMessages(
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal User user) {
        log.info("User {} retrieving messages for conversation {}", user.getId(), conversationId);

        try {
            Page<MessageResponse> messages = chatService.getMessages(conversationId, user.getId(), page, size);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", messages.getContent());
            result.put("currentPage", messages.getNumber());
            result.put("totalPages", messages.getTotalPages());
            result.put("totalItems", messages.getTotalElements());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error retrieving messages: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Đánh dấu tin nhắn đã đọc
     * PUT /api/user/chat/conversations/{conversationId}/read
     */
    @PutMapping("/conversations/{conversationId}/read")
    public ResponseEntity<?> markAsRead(
            @PathVariable Long conversationId,
            @AuthenticationPrincipal User user) {
        log.info("User {} marking messages as read in conversation {}", user.getId(), conversationId);

        try {
            chatService.markMessagesAsRead(conversationId, user.getId());

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Đã đánh dấu tin nhắn là đã đọc");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error marking messages as read: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Đếm tổng số tin nhắn chưa đọc
     * GET /api/user/chat/unread-count
     */
    @GetMapping("/unread-count")
    public ResponseEntity<?> getUnreadCount(@AuthenticationPrincipal User user) {
        log.info("User {} retrieving unread message count", user.getId());

        try {
            Long unreadCount = chatService.countUnreadMessages(user.getId());

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("unreadCount", unreadCount);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error counting unread messages: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Lấy danh sách tất cả users để chat
     * GET /api/user/chat/users
     */
    @GetMapping("/users")
    public ResponseEntity<?> searchUsers(
            @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal User user) {
        log.info("User {} searching users with keyword: {}", user.getId(), keyword);

        try {
            List<UserListResponse> users = chatService.searchUsers(user.getId(), keyword);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", users);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error searching users: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Upload file cho chat
     * POST /api/user/chat/upload
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User user) {
        log.info("User {} uploading chat file", user.getId());

        try {
            String fileUrl = chatService.uploadChatFile(file);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("fileUrl", fileUrl);
            result.put("fileName", file.getOriginalFilename());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error uploading file: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Gửi tin nhắn có file đính kèm
     * POST /api/user/chat/messages/file
     *
     * Content-Type: multipart/form-data
     * - data: JSON string (conversationId)
     * - file: File đính kèm (ảnh, pdf, max 5MB)
     */
    @PostMapping("/messages/file")
    public ResponseEntity<?> sendFileMessage(
            @RequestParam(value = "data", required = true) String dataJson,
            @RequestParam(value = "file", required = true) MultipartFile file,
            @AuthenticationPrincipal User user) {
        log.info("User {} sending file message", user.getId());

        try {
            // Parse conversation ID từ JSON
            Map<String, Object> data = objectMapper.readValue(dataJson, Map.class);
            Long conversationId = Long.valueOf(data.get("conversationId").toString());

            // Upload file vào FileMessage/conversation_X/
            String subfolderId = "conversation_" + conversationId;
            String fileUrl = fileStorageService.storeFile(file, FileStorageService.FileCategory.FILE_MESSAGE, subfolderId);
            log.info("File uploaded for conversation {}: {}", conversationId, fileUrl);

            // Tạo SendMessageRequest với file URL
            SendMessageRequest request = new SendMessageRequest();
            request.setConversationId(conversationId);
            request.setContent(fileUrl); // Lưu đường dẫn file vào content
            request.setMessageType("FILE");

            MessageResponse response = chatService.sendMessage(user.getId(), request);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Đã gửi file");
            result.put("data", response);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error sending file message: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Xóa conversation
     * DELETE /api/user/chat/conversations/{conversationId}
     */
    @DeleteMapping("/conversations/{conversationId}")
    public ResponseEntity<?> deleteConversation(
            @PathVariable Long conversationId,
            @AuthenticationPrincipal User user) {
        log.info("User {} deleting conversation {}", user.getId(), conversationId);

        try {
            chatService.deleteConversation(conversationId, user.getId());

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Đã xóa cuộc trò chuyện");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error deleting conversation: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}
