package com.example.FoodTourApp.DTO.ChatDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatNotification {
    private Long messageId;
    private Long conversationId;
    private Integer senderId;
    private String senderName;
    private String senderAvatar;
    private String content;
    private String messageType;
    private String fileName;
    private String mimeType;
    private Long fileSize;
    private LocalDateTime createdAt;
}
