package com.example.FoodTourApp.DTO.ChatbotDTO;

import lombok.Data;

@Data
public class SendChatbotMessageRequest {
    private Long conversationId;
    private String content;
}
