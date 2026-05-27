package com.example.FoodTourApp.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Lịch sử tìm kiếm của user (giống YouTube search history).
 * - Chỉ lưu khi user đã đăng nhập
 * - User có thể xóa từng item hoặc xóa toàn bộ
 * - Hiển thị khi user click vào thanh search
 */
@Entity
@Table(name = "search_histories", indexes = {
    @Index(name = "idx_search_history_user", columnList = "user_id"),
    @Index(name = "idx_search_history_user_time", columnList = "user_id, searched_at DESC"),
    @Index(name = "idx_search_history_query_norm", columnList = "query_normalized")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Query gốc user nhập (giữ nguyên dấu, chữ hoa thường)
     */
    @Column(name = "query_text", nullable = false, length = 500)
    private String queryText;

    /**
     * Query đã normalize (lowercase, bỏ dấu) - dùng để deduplicate
     */
    @Column(name = "query_normalized", nullable = false, length = 500)
    private String queryNormalized;

    @Column(name = "result_count")
    private Integer resultCount;

    @Column(name = "searched_at", nullable = false)
    private LocalDateTime searchedAt;

    @PrePersist
    protected void onCreate() {
        if (searchedAt == null) {
            searchedAt = LocalDateTime.now();
        }
    }
}
