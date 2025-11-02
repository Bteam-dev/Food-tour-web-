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
public class MessageResponse {
    private Long id;
    private Long conversationId;
    private Integer senderId;
    private String senderName;
    private String senderAvatar;
    private String content;
    private Boolean isRead;
    private String messageType;
    private LocalDateTime createdAt;
}

