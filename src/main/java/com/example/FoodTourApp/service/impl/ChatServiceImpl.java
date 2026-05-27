package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.DTO.ChatDTO.*;
import com.example.FoodTourApp.entity.Conversation;
import com.example.FoodTourApp.entity.Message;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.repository.ConversationRepository;
import com.example.FoodTourApp.repository.MessageRepository;
import com.example.FoodTourApp.repository.UserRepository;
import com.example.FoodTourApp.service.ChatPresenceService;
import com.example.FoodTourApp.service.ChatService;
import com.example.FoodTourApp.service.FCMService;
import com.example.FoodTourApp.service.MessageEncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatServiceImpl implements ChatService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final FileStorageService fileStorageService;
    private final FCMService fcmService;
    private final ChatPresenceService chatPresenceService;
    private final MessageEncryptionService encryptionService;

    private static final int DEFAULT_PAGE_SIZE = 30;

    @org.springframework.beans.factory.annotation.Value("${FILE_STORAGE_BASE_DIR}")
    private String storageBaseDir;

    @Override
    @Transactional
    public ConversationResponse createOrGetConversation(Integer currentUserId, Integer targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            throw new IllegalArgumentException("Không thể tạo cuộc trò chuyện với chính mình");
        }

        User otherUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng với ID: " + targetUserId));

        Conversation conversation = conversationRepository
                .findByTwoParticipants(currentUserId, otherUser.getId())
                .orElseGet(() -> {
                    User currentUser = userRepository.findById(currentUserId)
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

        // Validate: phải có nội dung (text hoặc URL file)
        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            throw new IllegalArgumentException("Nội dung tin nhắn không được để trống");
        }

        Conversation conversation = conversationRepository.findByIdWithParticipants(request.getConversationId())
                .orElseThrow(() -> new RuntimeException("Conversation không tồn tại"));

        boolean isParticipant = conversation.getParticipants().stream()
                .anyMatch(user -> user.getId().equals(senderId));

        if (!isParticipant) {
            throw new RuntimeException("Bạn không phải là thành viên của cuộc trò chuyện này");
        }

        // ── Kiểm tra blocked ───────────────────────────────────────────────
        // Nếu conversation bị chặn, người BỊ chặn (không phải người chặn) không gửi được
        if (conversation.isBlocked()) {
            Integer blockedById = conversation.getBlockedByUserId();
            // Người chặn vẫn gửi được, người bị chặn thì không
            boolean isSenderTheBlocker = senderId.equals(blockedById);
            if (!isSenderTheBlocker) {
                throw new RuntimeException("Bạn đã bị chặn trong cuộc trò chuyện này và không thể gửi tin nhắn.");
            }
        }
        // ──────────────────────────────────────────────────────────────────

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new RuntimeException("User không tồn tại"));

        // Xác định messageType (tự động nếu không truyền hoặc truyền sai)
        String rawType = request.getMessageType() == null ? "TEXT" : request.getMessageType().toUpperCase();
        Message.MessageType messageType;
        try {
            messageType = Message.MessageType.valueOf(rawType);
        } catch (IllegalArgumentException e) {
            messageType = Message.MessageType.TEXT;
        }

        // Giữ plaintext để dùng cho FCM, notification (không gửi ciphertext cho push)
        String plainContent = request.getContent();
        String encryptedContent = encryptionService.encrypt(plainContent);

        Message message = new Message();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setContent(encryptedContent);
        message.setMessageType(messageType);
        message.setFileName(request.getFileName());
        message.setMimeType(request.getMimeType());
        message.setFileSize(request.getFileSize());
        message.setIsRead(false);
        message.setCreatedAt(LocalDateTime.now());

        message = messageRepository.save(message);

        // Cập nhật snapshot cuộc trò chuyện (encrypted)
        String snippet = messageType == Message.MessageType.TEXT
                ? plainContent
                : "[" + messageType.name().toLowerCase() + "] " + (request.getFileName() != null ? request.getFileName() : "file");
        conversation.setLastMessage(encryptionService.encrypt(snippet));
        conversation.setLastMessageAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        MessageResponse response = mapToMessageResponse(message);
        final MessageResponse finalResponse = response;

        // 1. Broadcast realtime tới tất cả participant đang subscribe topic conversation
        messagingTemplate.convertAndSend(
                "/topic/conversation/" + request.getConversationId(),
                response
        );

        // 2. Với từng người nhận: gửi WS notification riêng + FCM nếu không active
        conversation.getParticipants().stream()
                .filter(user -> !user.getId().equals(senderId))
                .forEach(recipient -> {
                    // 2a. WS queue notification (hiển thị badge, update conversation list)
                    // Dùng plaintext cho notification — FE hiển thị trực tiếp
                    ChatNotification notification = ChatNotification.builder()
                            .messageId(finalResponse.getId())
                            .conversationId(conversation.getId())
                            .senderId(senderId)
                            .senderName(sender.getFullName())
                            .senderAvatar(sender.getAvatarUrl())
                            .content(plainContent)
                            .messageType(rawType)
                            .fileName(request.getFileName())
                            .mimeType(request.getMimeType())
                            .fileSize(request.getFileSize())
                            .createdAt(finalResponse.getCreatedAt())
                            .build();

                    messagingTemplate.convertAndSendToUser(
                            recipient.getId().toString(),
                            "/queue/messages",
                            notification
                    );

                    // 2b. FCM push: chỉ gửi nếu người nhận KHÔNG đang mở màn hình chat này
                    // Giống Messenger/Zalo: online + đang xem chat → không cần push
                    boolean isActiveInConversation = chatPresenceService
                            .isUserActiveInConversation(recipient.getId(), conversation.getId());

                    if (!isActiveInConversation) {
                        log.debug("Recipient {} is not active in conversation {} → sending FCM push",
                                recipient.getId(), conversation.getId());
                        fcmService.sendChatMessageNotification(
                                recipient,
                                sender.getFullName(),
                                sender.getAvatarUrl(),
                                conversation.getId(),
                                plainContent,
                                rawType
                        );
                    } else {
                        log.debug("Recipient {} is active in conversation {} → skipping FCM push",
                                recipient.getId(), conversation.getId());
                    }
                });

        return response;
    }

    /**
     * Cursor-based – KHÔNG dùng Page/COUNT.
     * Lấy N tin mới nhất, reverse thành ASC trả về client.
     */
    @Override
    @Transactional(readOnly = true)
    public List<MessageResponse> getLatestMessages(Long conversationId, Integer userId, int size) {
        checkParticipant(conversationId, userId);
        int limit = size > 0 ? size : DEFAULT_PAGE_SIZE;
        List<Message> messages = messageRepository.findLatestMessages(
                conversationId, PageRequest.of(0, limit));
        Collections.reverse(messages); // DESC → ASC
        return messages.stream().map(this::mapToMessageResponse).collect(Collectors.toList());
    }

    /**
     * Cursor-based – kéo lên load thêm tin cũ hơn.
     * Lấy N tin có id < beforeMessageId, reverse thành ASC.
     */
    @Override
    @Transactional(readOnly = true)
    public List<MessageResponse> getMessagesBefore(Long conversationId, Integer userId,
                                                    Long beforeMessageId, int size) {
        checkParticipant(conversationId, userId);
        int limit = size > 0 ? size : DEFAULT_PAGE_SIZE;
        List<Message> messages = messageRepository.findByConversationIdBeforeId(
                conversationId, beforeMessageId, PageRequest.of(0, limit));
        Collections.reverse(messages); // DESC → ASC
        return messages.stream().map(this::mapToMessageResponse).collect(Collectors.toList());
    }

    /** Kiểm tra participant, ném exception nếu không hợp lệ */
    private void checkParticipant(Long conversationId, Integer userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation không tồn tại"));
        boolean isParticipant = conversation.getParticipants().stream()
                .anyMatch(u -> u.getId().equals(userId));
        if (!isParticipant) throw new RuntimeException("Bạn không phải là thành viên của cuộc trò chuyện này");
    }

    @Override
    @Transactional
    public void markMessagesAsRead(Long conversationId, Integer userId) {
        checkParticipant(conversationId, userId); // ✅ thêm kiểm tra participant
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
        if (keyword == null || keyword.trim().isEmpty()) {
            return List.of();
        }

        String searchTerm = keyword.toLowerCase().trim();

        return userRepository.findAll().stream()
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
                .limit(10)
                .collect(Collectors.toList());
    }

    @Override
    public String uploadChatFile(MultipartFile file) throws Exception {
        return fileStorageService.storeChatFile(file, null);
    }

    @Override
    public java.util.Map<String, Object> uploadChatFileWithMeta(MultipartFile file, Integer userId, Long conversationId) {
        checkParticipant(conversationId, userId); // ✅ thêm kiểm tra participant trước khi upload
        try {
            String fileUrl = fileStorageService.storeChatFile(file, userId, conversationId);
            String contentType = file.getContentType();
            String messageType = FileStorageService.detectMessageType(contentType);

            java.util.Map<String, Object> result = new java.util.HashMap<>();
            result.put("success", true);
            result.put("fileUrl", fileUrl);
            result.put("fileName", file.getOriginalFilename());
            result.put("mimeType", contentType);
            result.put("fileSize", file.getSize());
            result.put("messageType", messageType);
            return result;
        } catch (java.io.IOException e) {
            throw new RuntimeException("Không thể upload file: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Resource serveChatFile(String relativePath, Integer userId) throws Exception {
        // Trích xuất conversationId từ path: user_{x}/conversation_{id}/filename
        Long conversationId = extractConversationIdFromPath(relativePath);
        if (conversationId == null) {
            throw new IllegalArgumentException("Path không hợp lệ: " + relativePath);
        }

        // Kiểm tra user có phải participant không
        if (!isParticipant(conversationId, userId)) {
            throw new SecurityException("Bạn không có quyền truy cập file này");
        }

        // Resolve đường dẫn vật lý, chống path traversal
        Path filePath = Paths.get(getFileMessageBaseDir())
                .resolve(relativePath.replace("/", "\\"))
                .normalize();

        if (!filePath.toAbsolutePath().startsWith(Paths.get(getFileMessageBaseDir()).toAbsolutePath())) {
            throw new SecurityException("Truy cập không hợp lệ");
        }

        if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
            throw new FileNotFoundException("File không tồn tại: " + relativePath);
        }

        // Decrypt file bytes trước khi serve (file trên disk đã được encrypt)
        byte[] encryptedBytes = Files.readAllBytes(filePath);
        byte[] decryptedBytes = encryptionService.decryptBytes(encryptedBytes);
        return new ByteArrayResource(decryptedBytes);
    }

    private Long extractConversationIdFromPath(String relativePath) {
        if (relativePath == null) return null;
        try {
            for (String part : relativePath.split("/")) {
                if (part.startsWith("conversation_")) {
                    return Long.parseLong(part.substring("conversation_".length()));
                }
            }
        } catch (NumberFormatException ignored) {}
        return null;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isParticipant(Long conversationId, Integer userId) {
        return conversationRepository.findById(conversationId)
                .map(conv -> conv.getParticipants().stream()
                        .anyMatch(u -> u.getId().equals(userId)))
                .orElse(false);
    }

    @Override
    @Transactional
    public void deleteConversation(Long conversationId, Integer userId) {
        // Chat không được phép xóa – liên quan đến lịch sử giao tiếp
        // Chỉ có thể chặn (block) nếu đối phương quá phiền phức
        throw new RuntimeException("Không thể xóa cuộc trò chuyện. Hãy sử dụng chức năng chặn (block) nếu đối phương quá phiền phức.");
    }

    @Override
    @Transactional
    public ConversationResponse blockConversation(Long conversationId, Integer userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation không tồn tại"));
        boolean isParticipant = conversation.getParticipants().stream()
                .anyMatch(u -> u.getId().equals(userId));
        if (!isParticipant) throw new RuntimeException("Bạn không phải thành viên của cuộc trò chuyện này");
        if (conversation.isBlocked()) throw new RuntimeException("Cuộc trò chuyện đã bị chặn rồi");

        conversation.setBlockedByUserId(userId);
        conversation.setBlockedAt(java.time.LocalDateTime.now());
        conversation.setUpdatedAt(java.time.LocalDateTime.now());
        conversationRepository.save(conversation);
        log.info("User {} blocked conversation {}", userId, conversationId);
        return mapToConversationResponse(conversation, userId);
    }

    @Override
    @Transactional
    public ConversationResponse unblockConversation(Long conversationId, Integer userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation không tồn tại"));
        if (!conversation.isBlocked()) throw new RuntimeException("Cuộc trò chuyện chưa bị chặn");
        if (!conversation.getBlockedByUserId().equals(userId))
            throw new RuntimeException("Chỉ người đã chặn mới có thể bỏ chặn");

        conversation.setBlockedByUserId(null);
        conversation.setBlockedAt(null);
        conversation.setUpdatedAt(java.time.LocalDateTime.now());
        conversationRepository.save(conversation);
        log.info("User {} unblocked conversation {}", userId, conversationId);
        return mapToConversationResponse(conversation, userId);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private String getFileMessageBaseDir() {
        return storageBaseDir + "\\FileMessage";
    }

    private ConversationResponse mapToConversationResponse(Conversation conversation, Integer currentUserId) {
        List<ConversationResponse.ParticipantInfo> participants = conversation.getParticipants().stream()
                .map(user -> ConversationResponse.ParticipantInfo.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .fullName(user.getFullName())
                        .avatarUrl(user.getAvatarUrl())
                        .build())
                .collect(Collectors.toList());

        Long unreadCount = (long) messageRepository.findUnreadMessages(conversation.getId(), currentUserId).size();

        return ConversationResponse.builder()
                .id(conversation.getId())
                .participants(participants)
                .lastMessage(encryptionService.decrypt(conversation.getLastMessage()))
                .lastMessageAt(conversation.getLastMessageAt())
                .unreadCount(unreadCount)
                .isBlocked(conversation.isBlocked())
                .blockedByUserId(conversation.getBlockedByUserId())
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
                .content(encryptionService.decrypt(message.getContent()))
                .isRead(message.getIsRead())
                .messageType(message.getMessageType().name())
                .fileName(message.getFileName())
                .mimeType(message.getMimeType())
                .fileSize(message.getFileSize())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
