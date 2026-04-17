package com.example.FoodTourApp.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Analytics tìm kiếm - lưu MỌI lượt search (cả guest lẫn user).
 * Dùng để:
 * 1. Tính trending searches (Colab)
 * 2. Train PhoBERT search model (Colab)
 * 3. Export search_analytics.csv cho Colab
 */
@Entity
@Table(name = "search_analytics", indexes = {
    @Index(name = "idx_analytics_query", columnList = "query_normalized"),
    @Index(name = "idx_analytics_time", columnList = "searched_at"),
    @Index(name = "idx_analytics_user", columnList = "user_id"),
    @Index(name = "idx_analytics_click", columnList = "clicked_product_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchAnalytics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * User ID (nullable - guest cũng được track)
     */
    @Column(name = "user_id")
    private Integer userId;

    /**
     * Query gốc
     */
    @Column(name = "query_text", nullable = false, length = 500)
    private String queryText;

    /**
     * Query normalized (lowercase, bỏ dấu)
     */
    @Column(name = "query_normalized", nullable = false, length = 500)
    private String queryNormalized;

    /**
     * Số kết quả trả về
     */
    @Column(name = "result_count")
    private Integer resultCount;

    /**
     * Product ID mà user click vào (nếu có)
     */
    @Column(name = "clicked_product_id")
    private Integer clickedProductId;

    /**
     * Vị trí của product trong kết quả search khi click
     */
    @Column(name = "click_position")
    private Integer clickPosition;

    /**
     * Session ID để group search trong cùng session
     */
    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Column(name = "searched_at", nullable = false)
    private LocalDateTime searchedAt;

    @PrePersist
    protected void onCreate() {
        if (searchedAt == null) {
            searchedAt = LocalDateTime.now();
        }
    }
}
