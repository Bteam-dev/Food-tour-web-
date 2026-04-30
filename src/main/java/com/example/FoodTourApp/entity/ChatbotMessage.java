package com.example.FoodTourApp.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "chatbot_messages")
@Data
public class ChatbotMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private ChatbotConversation conversation;

    @Column(name = "sender_type", nullable = false)
    private String senderType;  // "USER" hoặc "BOT"

    @Column(name = "sender_name", length = 100)
    private String senderName;  // Tên user hoặc "FoodTour Bot"

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    /**
     * JSON array các navigation URL gợi ý sản phẩm
     * Ví dụ: ["product_detail/8", "product_detail/7"]
     * Chỉ có ở BOT messages
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "navigation_urls", columnDefinition = "TEXT")
    private String navigationUrlsJson;

    /**
     * JSON array thông tin sản phẩm gợi ý (id, tên, giá, ảnh, ...)
     * Chỉ có ở BOT messages
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "suggested_products", columnDefinition = "TEXT")
    private String suggestedProductsJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
