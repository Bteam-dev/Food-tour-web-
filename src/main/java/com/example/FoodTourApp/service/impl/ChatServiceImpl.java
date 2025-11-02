package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.DTO.ChatDTO.*;
import com.example.FoodTourApp.entity.Conversation;
import com.example.FoodTourApp.entity.Message;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.repository.ConversationRepository;
import com.example.FoodTourApp.repository.MessageRepository;
import com.example.FoodTourApp.repository.UserRepository;
import com.example.FoodTourApp.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatServiceImpl implements ChatService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${file.upload-dir:uploads/chat}")
    private String uploadDir;

    @Override
    @Transactional
    public ConversationResponse createOrGetConversation(Integer currentUserId, Integer otherUserId) {
        if (currentUserId.equals(otherUserId)) {
            throw new IllegalArgumentException("Không thể tạo cuộc trò chuyện với chính mình");
        }

        // Tìm conversation đã tồn tại
        Conversation conversation = conversationRepository
                .findByTwoParticipants(currentUserId, otherUserId)
                .orElseGet(() -> {
                    // Tạo conversation mới
                    User currentUser = userRepository.findById(currentUserId)
                            .orElseThrow(() -> new RuntimeException("User không tồn tại"));
                    User otherUser = userRepository.findById(otherUserId)
                            .orElseThrow(() -> new RuntimeException("User không tồn tại"));

                    Conversation newConv = new Conversation();
                    Set<User> participants = new HashSet<>();
                    participants.add(currentUser);
                    participants.add(otherUser);
                    newConv.setParticipants(participants);
                    newConv.setCreatedAt(LocalDateTime.now());
                    newConv.setUpdatedAt(LocalDateTime.now());

                    return conversationRepository.save(newConv);
                });

        return mapToConversationResponse(conversation, currentUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationResponse> getUserConversations(Integer userId) {
        List<Conversation> conversations = conversationRepository.findByParticipantId(userId);
        return conversations.stream()
                .map(conv -> mapToConversationResponse(conv, userId))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public MessageResponse sendMessage(Integer senderId, SendMessageRequest request) {
        log.info("Sending message - SenderId: {}, ConversationId: {}", senderId, request.getConversationId());

        Conversation conversation = conversationRepository.findByIdWithParticipants(request.getConversationId())
                .orElseThrow(() -> new RuntimeException("Conversation không tồn tại"));

        log.info("Conversation found - ID: {}, Participants count: {}", conversation.getId(), conversation.getParticipants().size());
        conversation.getParticipants().forEach(p -> log.info("Participant: ID={}, Email={}", p.getId(), p.getEmail()));

        // Kiểm tra xem sender có phải là participant không
        boolean isParticipant = conversation.getParticipants().stream()
                .anyMatch(user -> user.getId().equals(senderId));

        log.info("Is sender {} a participant? {}", senderId, isParticipant);

        if (!isParticipant) {
            throw new RuntimeException("Bạn không phải là thành viên của cuộc trò chuyện này");
        }

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new RuntimeException("User không tồn tại"));

        // Tạo message
        Message message = new Message();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setContent(request.getContent());
        message.setMessageType(Message.MessageType.valueOf(request.getMessageType()));
        message.setIsRead(false);
        message.setCreatedAt(LocalDateTime.now());

        message = messageRepository.save(message);

        // Cập nhật conversation
        conversation.setLastMessage(request.getContent());
        conversation.setLastMessageAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        MessageResponse response = mapToMessageResponse(message);

        // Gửi notification đến các user khác trong conversation qua WebSocket
        final Message savedMessage = message; // Tạo final reference cho lambda
        conversation.getParticipants().stream()
                .filter(user -> !user.getId().equals(senderId))
                .forEach(user -> {
                    ChatNotification notification = new ChatNotification();
                    notification.setMessageId(savedMessage.getId());
                    notification.setConversationId(conversation.getId());
                    notification.setSenderId(senderId);
                    notification.setSenderName(sender.getFullName());
                    notification.setContent(request.getContent());
                    notification.setMessageType(request.getMessageType());

                    messagingTemplate.convertAndSendToUser(
                            user.getId().toString(),
                            "/queue/messages",
                            notification
                    );
                });

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MessageResponse> getMessages(Long conversationId, Integer userId, int page, int size) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation không tồn tại"));

        // Kiểm tra xem user có phải là participant không
        boolean isParticipant = conversation.getParticipants().stream()
                .anyMatch(user -> user.getId().equals(userId));
        if (!isParticipant) {
            throw new RuntimeException("Bạn không phải là thành viên của cuộc trò chuyện này");
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtDesc(conversationId, pageable);

        return messages.map(this::mapToMessageResponse);
    }

    @Override
    @Transactional
    public void markMessagesAsRead(Long conversationId, Integer userId) {
        List<Message> unreadMessages = messageRepository.findUnreadMessages(conversationId, userId);
        unreadMessages.forEach(message -> message.setIsRead(true));
        messageRepository.saveAll(unreadMessages);
    }

    @Override
    @Transactional(readOnly = true)
    public Long countUnreadMessages(Integer userId) {
        return messageRepository.countUnreadMessagesByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserListResponse> searchUsers(Integer currentUserId, String keyword) {
        List<User> users = userRepository.findAll();

        if (keyword == null || keyword.trim().isEmpty()) {
            return List.of(); // Trả về list rỗng nếu không có keyword
        }

        String searchTerm = keyword.toLowerCase().trim();

        return users.stream()
                .filter(user -> !user.getId().equals(currentUserId) && user.getIsActive())
                .filter(user ->
                    user.getEmail().toLowerCase().contains(searchTerm) ||
                    user.getFullName().toLowerCase().contains(searchTerm) ||
                    user.getUsername().toLowerCase().contains(searchTerm)
                )
                .map(user -> UserListResponse.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .fullName(user.getFullName())
                        .avatarUrl(user.getAvatarUrl())
                        .email(user.getEmail())
                        .isActive(user.getIsActive())
                        .build())
                .limit(10) // Giới hạn 10 kết quả
                .collect(Collectors.toList());
    }

    @Override
    public String uploadChatFile(MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File không được để trống");
        }

        // Tạo thư mục nếu chưa tồn tại
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // Tạo tên file unique
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String uniqueFilename = System.currentTimeMillis() + "-" + UUID.randomUUID() + extension;

        // Lưu file
        Path filePath = uploadPath.resolve(uniqueFilename);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // Trả về URL
        return "/uploads/chat/" + uniqueFilename;
    }

    @Override
    @Transactional
    public void deleteConversation(Long conversationId, Integer userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation không tồn tại"));

        // Kiểm tra xem user có phải là participant không
        boolean isParticipant = conversation.getParticipants().stream()
                .anyMatch(user -> user.getId().equals(userId));

        if (!isParticipant) {
            throw new RuntimeException("Bạn không có quyền xóa cuộc trò chuyện này");
        }

        // Xóa conversation (cascade sẽ tự động xóa messages và participants)
        conversationRepository.delete(conversation);
        log.info("User {} deleted conversation {}", userId, conversationId);
    }

    // Helper methods
    private ConversationResponse mapToConversationResponse(Conversation conversation, Integer currentUserId) {
        List<ConversationResponse.ParticipantInfo> participants = conversation.getParticipants().stream()
                .map(user -> ConversationResponse.ParticipantInfo.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .fullName(user.getFullName())
                        .avatarUrl(user.getAvatarUrl())
                        .build())
                .collect(Collectors.toList());

        // Đếm tin nhắn chưa đọc
        Long unreadCount = (long) messageRepository.findUnreadMessages(conversation.getId(), currentUserId).size();

        return ConversationResponse.builder()
                .id(conversation.getId())
                .participants(participants)
                .lastMessage(conversation.getLastMessage())
                .lastMessageAt(conversation.getLastMessageAt())
                .unreadCount(unreadCount)
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }

    private MessageResponse mapToMessageResponse(Message message) {
        return MessageResponse.builder()
                .id(message.getId())
                .conversationId(message.getConversation().getId())
                .senderId(message.getSender().getId())
                .senderName(message.getSender().getFullName())
                .senderAvatar(message.getSender().getAvatarUrl())
                .content(message.getContent())
                .isRead(message.getIsRead())
                .messageType(message.getMessageType().name())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
