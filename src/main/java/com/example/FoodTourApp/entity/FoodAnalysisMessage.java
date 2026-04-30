package com.example.FoodTourApp.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Một tin nhắn trong phiên phân tích món ăn.
 * Độc lập hoàn toàn với ChatbotMessage (chatbot chung).
 */
@Entity
@Table(name = "food_analysis_messages")
@Data
public class FoodAnalysisMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private FoodAnalysisSession session;

    /** "USER" hoặc "BOT" */
    @Column(name = "sender_type", nullable = false, length = 10)
    private String senderType;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
