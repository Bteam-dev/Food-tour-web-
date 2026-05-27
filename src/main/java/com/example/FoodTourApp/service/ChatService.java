package com.example.FoodTourApp.service;

import com.example.FoodTourApp.DTO.ChatDTO.*;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ChatService {

    /**
     * Tạo hoặc lấy conversation – nhận thẳng targetUserId (flow giống Facebook/Zalo)
     */
    ConversationResponse createOrGetConversation(Integer currentUserId, Integer targetUserId);

    /**
     * Lấy danh sách conversation của user
     */
    List<ConversationResponse> getUserConversations(Integer userId);

    /**
     * Gửi tin nhắn
     */
    MessageResponse sendMessage(Integer senderId, SendMessageRequest request);

    /**
     * Load tin nhắn mới nhất khi mở chat (cursor-based, không dùng Page).
     * Trả về list ASC (cũ → mới).
     *
     * @param size số lượng, mặc định 30
     */
    List<MessageResponse> getLatestMessages(Long conversationId, Integer userId, int size);

    /**
     * Load thêm tin nhắn cũ hơn khi kéo lên (cursor-based).
     * Trả về list ASC (cũ → mới) để client prepend.
     *
     * @param beforeMessageId ID tin nhắn cũ nhất đang hiển thị
     * @param size            số lượng, mặc định 30
     */
    List<MessageResponse> getMessagesBefore(Long conversationId, Integer userId, Long beforeMessageId, int size);

    /**
     * Đánh dấu tin nhắn đã đọc
     */
    void markMessagesAsRead(Long conversationId, Integer userId);

    /**
     * Đếm tổng số tin nhắn chưa đọc
     */
    Long countUnreadMessages(Integer userId);

    /**
     * Tìm kiếm users theo email hoặc tên (để add vào chat)
     */
    List<UserListResponse> searchUsers(Integer currentUserId, String keyword);

    /**
     * Upload file cho chat
     */
    String uploadChatFile(MultipartFile file) throws Exception;

    /**
     * Upload file chat với userId và conversationId để lưu đúng thư mục.
     * Trả về Map chứa fileUrl, fileName, mimeType, fileSize, messageType.
     */
    java.util.Map<String, Object> uploadChatFileWithMeta(MultipartFile file, Integer userId, Long conversationId);

    /**
     * Kiểm tra user có phải participant của conversation không
     */
    boolean isParticipant(Long conversationId, Integer userId);

    /**
     * Serve file chat – kiểm tra JWT + participant, trả về Resource để stream
     * @param relativePath  vd: user_3/conversation_1/filename.mp4
     * @param userId        user đang request
     * @return Resource của file
     * @throws SecurityException nếu user không có quyền
     * @throws java.io.FileNotFoundException nếu file không tồn tại
     */
    Resource serveChatFile(String relativePath, Integer userId) throws Exception;

    /**
     * Xóa conversation (soft delete hoặc hard delete)
     */
    void deleteConversation(Long conversationId, Integer userId);

    /**
     * Chặn cuộc trò chuyện (blockedByUserId = userId)
     * Người bị chặn không thể gửi tin nhắn trong conversation đó
     */
    ConversationResponse blockConversation(Long conversationId, Integer userId);

    /**
     * Bỏ chặn cuộc trò chuyện (chỉ người đã chặn mới có thể bỏ)
     */
    ConversationResponse unblockConversation(Long conversationId, Integer userId);
}
