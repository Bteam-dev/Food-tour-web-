package com.example.FoodTourApp.DTO.ChatbotDTO;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatbotMessageResponse {
    private Long id;
    private String senderType;
    private String senderName;
    private String content;
    private LocalDateTime createdAt;
}
