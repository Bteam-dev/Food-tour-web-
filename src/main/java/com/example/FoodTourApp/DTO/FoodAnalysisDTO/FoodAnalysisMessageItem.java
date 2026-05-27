package com.example.FoodTourApp.DTO.FoodAnalysisDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Một tin nhắn trong lịch sử phiên phân tích (dùng cho GET history) */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoodAnalysisMessageItem {

    private Long id;
    private String senderType;   // "USER" | "BOT"
    private String content;
    private LocalDateTime createdAt;
}
