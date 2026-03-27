package com.example.FoodTourApp.entity;

import com.example.FoodTourApp.config.ProductEsSyncListener;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.math.RoundingMode;

@EntityListeners(ProductEsSyncListener.class)
@Entity
@Table(name = "products")
@Data
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "price", nullable = false, precision = 15, scale = 2)
    private BigDecimal price;

    @Column(name = "discount_price", precision = 15, scale = 2)
    private BigDecimal discountPrice;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "image_urls", columnDefinition = "TEXT")
    private String imageUrls; // JSON array: ["url1","url2","url3"]

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ingredients", columnDefinition = "TEXT")
    private String ingredients; // JSON array: ["bánh phở","thịt bò","hành"]

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "nutrition_info", columnDefinition = "TEXT")
    private String nutritionInfo; // JSON array: ["calories: 350","protein: 25g","fat: 10g","carbs: 40g"]

    @Column(name = "preparation_time")
    private Integer preparationTime;

    @Column(name = "is_available", nullable = false)
    private Boolean isAvailable = true;

    @Column(name = "stock_quantity")
    private Integer stockQuantity = 0;

    @Column(name = "min_order_quantity")
    private Integer minOrderQuantity = 1;

    @Column(name = "max_order_quantity")
    private Integer maxOrderQuantity = 999;

    @Column(name = "rating")
    private Double rating = 0.0;

    @Column(name = "total_reviews")
    private Long totalReviews = 0L;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags", columnDefinition = "TEXT")
    private String tags; // JSON array: ["spicy","vegetarian","bestseller"]

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    /**
     * Lấy giá hiệu lực của sản phẩm
     * Nếu có giá giảm (discountPrice) thì ưu tiên giá giảm
     * Nếu không có giá giảm thì dùng giá gốc (price)
     *
     * @return Giá hiệu lực để tính toán đơn hàng, cart, commission
     */
    public BigDecimal getEffectivePrice() {
        return (discountPrice != null && discountPrice.compareTo(BigDecimal.ZERO) > 0)
                ? discountPrice
                : price;
    }

    /**
     * Kiểm tra xem sản phẩm có đang giảm giá không
     *
     * @return true nếu có giá giảm hợp lệ
     */
    public boolean hasDiscount() {
        return discountPrice != null
                && discountPrice.compareTo(BigDecimal.ZERO) > 0
                && discountPrice.compareTo(price) < 0;
    }

    /**
     * Tính % giảm giá
     *
     * @return Phần trăm giảm giá (0-100)
     */
    public BigDecimal getDiscountPercentage() {
        if (!hasDiscount()) {
            return BigDecimal.ZERO;
        }
        return price.subtract(discountPrice)
                .divide(price, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);
    }
}