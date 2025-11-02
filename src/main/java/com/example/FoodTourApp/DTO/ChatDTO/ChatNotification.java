package com.example.FoodTourApp.DTO.ChatDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatNotification {
    private Long messageId;
    private Long conversationId;
    private Integer senderId;
    private String senderName;
    private String content;
    private String messageType;
}


