package com.example.FoodTourApp.service;

import com.example.FoodTourApp.DTO.ChatDTO.*;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ChatService {

    /**
     * Tạo hoặc lấy conversation giữa 2 user
     */
    ConversationResponse createOrGetConversation(Integer currentUserId, Integer otherUserId);

    /**
     * Lấy danh sách conversation của user
     */
    List<ConversationResponse> getUserConversations(Integer userId);

    /**
     * Gửi tin nhắn
     */
    MessageResponse sendMessage(Integer senderId, SendMessageRequest request);

    /**
     * Lấy lịch sử tin nhắn
     */
    Page<MessageResponse> getMessages(Long conversationId, Integer userId, int page, int size);

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
     * Xóa conversation (soft delete hoặc hard delete)
     */
    void deleteConversation(Long conversationId, Integer userId);
}
