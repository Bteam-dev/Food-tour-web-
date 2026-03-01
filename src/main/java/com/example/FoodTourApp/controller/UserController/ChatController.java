package com.example.FoodTourApp.controller.UserController;

import com.example.FoodTourApp.DTO.ChatDTO.*;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    // ─── Conversations ────────────────────────────────────────────────────────

    /**
     * Tạo hoặc lấy conversation với user có id = targetUserId.
     *
     * Flow giống Facebook / Zalo:
     *   1. GET /api/user/chat/users?keyword=... → server trả list user kèm id
     *   2. User bấm chọn người → client đã có id từ bước 1
     *   3. POST /api/user/chat/conversations/with/{targetUserId}  ← id đi thẳng vào URL
     *   4. Server trả về conversationId → dùng cho mọi action sau
     */
    @PostMapping("/conversations/with/{targetUserId}")
    public ResponseEntity<?> createOrGetConversation(
            @PathVariable Integer targetUserId,
            @AuthenticationPrincipal User user) {
        try {
            ConversationResponse response = chatService.createOrGetConversation(user.getId(), targetUserId);
            return ok("Cuộc trò chuyện đã sẵn sàng", response);
        } catch (Exception e) {
            return error(e);
        }
    }

    /** GET /api/user/chat/conversations */
    @GetMapping("/conversations")
    public ResponseEntity<?> getUserConversations(@AuthenticationPrincipal User user) {
        try {
            List<ConversationResponse> conversations = chatService.getUserConversations(user.getId());
            return ok(null, conversations);
        } catch (Exception e) {
            return error(e);
        }
    }

    /** DELETE /api/user/chat/conversations/{conversationId} */
    @DeleteMapping("/conversations/{conversationId}")
    public ResponseEntity<?> deleteConversation(
            @PathVariable Long conversationId,
            @AuthenticationPrincipal User user) {
        try {
            chatService.deleteConversation(conversationId, user.getId());
            return ok("Đã xóa cuộc trò chuyện", null);
        } catch (Exception e) {
            return error(e);
        }
    }

    // ─── Messages ────────────────────────────────────────────────────────────

    /**
     * Mở chat → load 30 tin MỚI NHẤT.
     * GET /api/user/chat/conversations/{conversationId}/messages?size=30
     * Trả về ASC (cũ → mới), hasMore = còn tin cũ hơn để kéo lên không.
     */
    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<?> getLatestMessages(
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "30") int size,
            @AuthenticationPrincipal User user) {
        try {
            List<MessageResponse> messages = chatService.getLatestMessages(conversationId, user.getId(), size);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", messages);
            // hasMore: nếu trả về đủ size thì có thể còn tin cũ hơn
            result.put("hasMore", messages.size() == size);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return error(e);
        }
    }

    /**
     * Kéo lên load thêm → 30 tin CŨ HƠN beforeMessageId.
     * GET /api/user/chat/conversations/{conversationId}/messages/before/{beforeMessageId}?size=30
     * Trả về ASC (cũ → mới) để client prepend vào đầu danh sách.
     */
    @GetMapping("/conversations/{conversationId}/messages/before/{beforeMessageId}")
    public ResponseEntity<?> getMessagesBefore(
            @PathVariable Long conversationId,
            @PathVariable Long beforeMessageId,
            @RequestParam(defaultValue = "30") int size,
            @AuthenticationPrincipal User user) {
        try {
            List<MessageResponse> messages = chatService.getMessagesBefore(
                    conversationId, user.getId(), beforeMessageId, size);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", messages);
            result.put("hasMore", messages.size() == size);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return error(e);
        }
    }

    /** PUT /api/user/chat/conversations/{conversationId}/read */
    @PutMapping("/conversations/{conversationId}/read")
    public ResponseEntity<?> markAsRead(
            @PathVariable Long conversationId,
            @AuthenticationPrincipal User user) {
        try {
            chatService.markMessagesAsRead(conversationId, user.getId());
            return ok("Đã đánh dấu tin nhắn là đã đọc", null);
        } catch (Exception e) {
            return error(e);
        }
    }

    /** GET /api/user/chat/unread-count */
    @GetMapping("/unread-count")
    public ResponseEntity<?> getUnreadCount(@AuthenticationPrincipal User user) {
        try {
            Long unreadCount = chatService.countUnreadMessages(user.getId());
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("unreadCount", unreadCount);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return error(e);
        }
    }

    // ─── Users ───────────────────────────────────────────────────────────────

    /** GET /api/user/chat/users?keyword=... */
    @GetMapping("/users")
    public ResponseEntity<?> searchUsers(
            @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal User user) {
        try {
            List<UserListResponse> users = chatService.searchUsers(user.getId(), keyword);
            return ok(null, users);
        } catch (Exception e) {
            return error(e);
        }
    }

    // ─── File upload ─────────────────────────────────────────────────────────

    /**
     * BƯỚC 1: Upload file, nhận về URL + metadata.
     * POST /api/user/chat/conversations/{conversationId}/upload
     * Multipart field: "file"
     *
     * BƯỚC 2 (do CLIENT tự làm): dùng URL nhận được để gửi tin nhắn qua WebSocket
     *   stompClient.send("/app/chat.send", {}, JSON.stringify({
     *       conversationId: 5,
     *       content:     "https://.../FileMessage/...",
     *       messageType: "IMAGE",          // IMAGE | VIDEO | AUDIO | FILE
     *       fileName:    "photo.jpg",
     *       mimeType:    "image/jpeg",
     *       fileSize:    204800
     *   }));
     *
     * Luồng này giống Facebook/Zalo:
     *   upload (HTTP multipart) → nhận URL → gửi message (WebSocket) → broadcast realtime
     */
    @PostMapping("/conversations/{conversationId}/upload")
    public ResponseEntity<?> uploadChatFile(
            @PathVariable Long conversationId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User user) {
        log.info("User {} uploading file for conversation {}: {} ({})",
                user.getId(), conversationId, file.getOriginalFilename(), file.getContentType());
        try {
            Map<String, Object> result = chatService.uploadChatFileWithMeta(file, user.getId(), conversationId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return error(e);
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> ok(String message, Object data) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        if (message != null) result.put("message", message);
        if (data != null) result.put("data", data);
        return ResponseEntity.ok(result);
    }

    private ResponseEntity<Map<String, Object>> error(Exception e) {
        log.error("ChatController error: {}", e.getMessage(), e);
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", e.getMessage());
        return ResponseEntity.badRequest().body(result);
    }
}
