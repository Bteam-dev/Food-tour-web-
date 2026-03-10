package com.example.FoodTourApp.DTO.ChatbotDTO;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatbotMessageResponse {
    private Long id;
    private String senderType;   // "USER" or "BOT"
    private String senderName;   // Tên user hoặc "FoodTour Bot"
    private String content;
    private LocalDateTime createdAt;
}
