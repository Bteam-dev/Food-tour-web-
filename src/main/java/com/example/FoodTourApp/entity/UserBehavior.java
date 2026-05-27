package com.example.FoodTourApp.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity lưu trữ hành vi người dùng để train recommendation model
 * Giống như YouTube tracking: view, like, watch_time, click, etc.
 */
@Entity
@Table(name = "user_behaviors", indexes = {
    @Index(name = "idx_user_behavior_user", columnList = "user_id"),
    @Index(name = "idx_user_behavior_product", columnList = "product_id"),
    @Index(name = "idx_user_behavior_type", columnList = "action_type"),
    @Index(name = "idx_user_behavior_time", columnList = "created_at"),
    @Index(name = "idx_user_behavior_session", columnList = "session_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBehavior {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
    
    /**
     * Loại hành vi:
     * - VIEW: Xem chi tiết sản phẩm
     * - CLICK: Click vào sản phẩm từ danh sách
     * - ADD_CART: Thêm vào giỏ hàng
     * - REMOVE_CART: Xóa khỏi giỏ hàng
     * - WISHLIST_ADD: Thêm vào wishlist
     * - WISHLIST_REMOVE: Xóa khỏi wishlist
     * - PURCHASE: Mua hàng thành công
     * - REVIEW_POSITIVE: Review >= 4 sao
     * - REVIEW_NEGATIVE: Review <= 2 sao
     * - SEARCH_CLICK: Click từ kết quả search
     * - RECOMMEND_CLICK: Click từ gợi ý
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 30)
    private ActionType actionType;
    
    /**
     * Trọng số của hành vi (dùng cho training):
     * - VIEW: 1.0
     * - CLICK: 1.5
     * - ADD_CART: 3.0
     * - PURCHASE: 5.0
     * - REVIEW_POSITIVE: 4.0
     * - REVIEW_NEGATIVE: -2.0
     * - WISHLIST_ADD: 2.0
     */
    @Column(name = "weight", nullable = false)
    private Double weight;
    
    /**
     * Thời gian xem (giây) - chỉ có ý nghĩa với VIEW
     * Giống watch_time của YouTube
     */
    @Column(name = "view_duration_seconds")
    private Integer viewDurationSeconds;
    
    /**
     * Số lượng (cho PURCHASE, ADD_CART)
     */
    @Column(name = "quantity")
    private Integer quantity;
    
    /**
     * Rating (cho REVIEW_POSITIVE, REVIEW_NEGATIVE)
     */
    @Column(name = "rating")
    private Integer rating;
    
    /**
     * Session ID để group các hành vi trong cùng session
     * Giúp tính session-based recommendation
     */
    @Column(name = "session_id", length = 64)
    private String sessionId;
    
    /**
     * Nguồn của hành vi (để phân tích)
     * - SEARCH: từ trang tìm kiếm
     * - HOME: từ trang chủ
     * - CATEGORY: từ trang danh mục
     * - RECOMMENDATION: từ gợi ý
     * - SIMILAR: từ sản phẩm tương tự
     * - DIRECT: truy cập trực tiếp
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 20)
    private BehaviorSource source;
    
    /**
     * Category của sản phẩm tại thời điểm hành vi (snapshot)
     */
    @Column(name = "category_id")
    private Integer categoryId;
    
    /**
     * Shop của sản phẩm tại thời điểm hành vi (snapshot)
     */
    @Column(name = "shop_id")
    private Integer shopId;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (weight == null) {
            weight = actionType.getDefaultWeight();
        }
    }
    
    public enum ActionType {
        VIEW(1.0), // không thấy
        CLICK(1.5), //thấy
        SEARCH_CLICK(2.0), // thấy
        RECOMMEND_CLICK(2.5), // thấy
        ADD_CART(3.0), // thấy
        REMOVE_CART(-1.0), // thấy
        WISHLIST_ADD(2.0), // thấy
        WISHLIST_REMOVE(-0.5),
        PURCHASE(5.0), // thấy
        REVIEW_POSITIVE(4.0),
        REVIEW_NEGATIVE(-2.0);
        
        private final double defaultWeight;
        
        ActionType(double defaultWeight) {
            this.defaultWeight = defaultWeight;
        }
        
        public double getDefaultWeight() {
            return defaultWeight;
        }
    }
    
    public enum BehaviorSource {
        SEARCH, // hoạt động
        HOME, // hoạt động
        CATEGORY, // hoạt động
        RECOMMENDATION, //họat động
        SIMILAR, // không hoạt động 
        DIRECT //hoạt động
    }
}
