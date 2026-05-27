package com.example.FoodTourApp.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Một phiên hỏi đáp phân tích món ăn cụ thể — yêu cầu đăng nhập.
 * Độc lập hoàn toàn với ChatbotConversation (chatbot chung).
 * Mỗi user × product có tối đa một phiên đang hoạt động.
 */
@Entity
@Table(name = "food_analysis_sessions",
        indexes = {
            @Index(name = "idx_fas_user_product", columnList = "user_id, product_id"),
            @Index(name = "idx_fas_session_key", columnList = "session_key", unique = true)
        })
@Data
public class FoodAnalysisSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** UUID server tạo khi khởi tạo phiên */
    @Column(name = "session_key", nullable = false, length = 64, unique = true)
    private String sessionKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<FoodAnalysisMessage> messages = new ArrayList<>();

    public void addMessage(FoodAnalysisMessage msg) {
        messages.add(msg);
        msg.setSession(this);
        updatedAt = LocalDateTime.now();
    }
}
