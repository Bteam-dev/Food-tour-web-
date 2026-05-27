package com.example.FoodTourApp.DTO.WishlistDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WishlistItemDTO {
    private Integer wishlistId;
    private Integer productId;
    private String productName;
    private String description;
    private BigDecimal price;
    private BigDecimal discountPrice;
    private List<String> imageUrls;
    private Boolean isAvailable;
    private Integer stockQuantity;
    private Double averageRating;
    private Long totalReviews;  // Sửa từ Integer sang Long

    // Thông tin giảm giá
    private Boolean hasDiscount;           // Có đang giảm giá không
    private BigDecimal discountPercentage; // % giảm giá (để hiển thị badge)
    private BigDecimal effectivePrice;     // Giá hiệu lực (price hoặc discountPrice)

    // Thông tin shop
    private Integer shopId;
    private String shopName;
    private String shopLogoUrl;
    private Boolean shopIsActive;

    // Thông tin category
    private Integer categoryId;
    private String categoryName;

    // Thông tin wishlist
    private LocalDateTime addedAt;
}
