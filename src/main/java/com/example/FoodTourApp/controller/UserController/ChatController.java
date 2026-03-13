package com.example.FoodTourApp.controller.UserController;

import com.example.FoodTourApp.DTO.ChatDTO.*;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.service.ChatService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

    @PostMapping("/conversations/with/{targetUserId}")
    public ResponseEntity<?> createOrGetConversation(
            @PathVariable Integer targetUserId,
            @AuthenticationPrincipal User user) {
        try {
            return ok("Cuộc trò chuyện đã sẵn sàng", chatService.createOrGetConversation(user.getId(), targetUserId));
        } catch (Exception e) { return error(e); }
    }

    @GetMapping("/conversations")
    public ResponseEntity<?> getUserConversations(@AuthenticationPrincipal User user) {
        try {
            return ok(null, chatService.getUserConversations(user.getId()));
        } catch (Exception e) { return error(e); }
    }

    @DeleteMapping("/conversations/{conversationId}")
    public ResponseEntity<?> deleteConversation(
            @PathVariable Long conversationId,
            @AuthenticationPrincipal User user) {
        try {
            chatService.deleteConversation(conversationId, user.getId());
            return ok("Đã xóa cuộc trò chuyện", null);
        } catch (Exception e) { return error(e); }
    }

    // ─── Messages ────────────────────────────────────────────────────────────

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
            result.put("hasMore", messages.size() == size);
            return ResponseEntity.ok(result);
        } catch (Exception e) { return error(e); }
    }

    @GetMapping("/conversations/{conversationId}/messages/before/{beforeMessageId}")
    public ResponseEntity<?> getMessagesBefore(
            @PathVariable Long conversationId,
            @PathVariable Long beforeMessageId,
            @RequestParam(defaultValue = "30") int size,
            @AuthenticationPrincipal User user) {
        try {
            List<MessageResponse> messages = chatService.getMessagesBefore(conversationId, user.getId(), beforeMessageId, size);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", messages);
            result.put("hasMore", messages.size() == size);
            return ResponseEntity.ok(result);
        } catch (Exception e) { return error(e); }
    }

    @PutMapping("/conversations/{conversationId}/read")
    public ResponseEntity<?> markAsRead(
            @PathVariable Long conversationId,
            @AuthenticationPrincipal User user) {
        try {
            chatService.markMessagesAsRead(conversationId, user.getId());
            return ok("Đã đánh dấu tin nhắn là đã đọc", null);
        } catch (Exception e) { return error(e); }
    }

    @GetMapping("/unread-count")
    public ResponseEntity<?> getUnreadCount(@AuthenticationPrincipal User user) {
        try {
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("unreadCount", chatService.countUnreadMessages(user.getId()));
            return ResponseEntity.ok(result);
        } catch (Exception e) { return error(e); }
    }

    // ─── Users ───────────────────────────────────────────────────────────────

    @GetMapping("/users")
    public ResponseEntity<?> searchUsers(
            @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal User user) {
        try {
            return ok(null, chatService.searchUsers(user.getId(), keyword));
        } catch (Exception e) { return error(e); }
    }

    // ─── File upload ─────────────────────────────────────────────────────────

    @PostMapping("/conversations/{conversationId}/upload")
    public ResponseEntity<?> uploadChatFile(
            @PathVariable Long conversationId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User user) {
        try {
            return ResponseEntity.ok(chatService.uploadChatFileWithMeta(file, user.getId(), conversationId));
        } catch (Exception e) { return error(e); }
    }

    // ─── Serve file chat (private) ────────────────────────────────────────────

    /**
     * GET /api/user/chat/files/user_{userId}/conversation_{conversationId}/{filename}
     * Chỉ participant của conversation mới được tải file
     */
    @GetMapping("/files/**")
    public ResponseEntity<Resource> serveChatFile(
            HttpServletRequest request,
            @AuthenticationPrincipal User user) {
        try {
            String prefix = "/api/user/chat/files/";
            String uri = request.getRequestURI();
            String relativePath = uri.substring(uri.indexOf(prefix) + prefix.length());

            Resource resource = chatService.serveChatFile(relativePath, user.getId());

            String contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
            if (contentType == null) contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);

        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        } catch (Exception e) {
            log.error("serveChatFile error: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    // ─── Block / Unblock ─────────────────────────────────────────────────────

    /**
     * Chặn cuộc trò chuyện – người bị chặn không gửi được tin nhắn
     * POST /api/user/chat/conversations/{conversationId}/block
     */
    @PostMapping("/conversations/{conversationId}/block")
    public ResponseEntity<?> blockConversation(
            @PathVariable Long conversationId,
            @AuthenticationPrincipal User user) {
        try {
            return ok("Đã chặn cuộc trò chuyện", chatService.blockConversation(conversationId, user.getId()));
        } catch (Exception e) { return error(e); }
    }

    /**
     * Bỏ chặn cuộc trò chuyện – chỉ người đã chặn mới bỏ được
     * POST /api/user/chat/conversations/{conversationId}/unblock
     */
    @PostMapping("/conversations/{conversationId}/unblock")
    public ResponseEntity<?> unblockConversation(
            @PathVariable Long conversationId,
            @AuthenticationPrincipal User user) {
        try {
            return ok("Đã bỏ chặn cuộc trò chuyện", chatService.unblockConversation(conversationId, user.getId()));
        } catch (Exception e) { return error(e); }
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
