package com.example.FoodTourApp.DTO.RecommendDTO;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;


@Data
@Builder
public class ProductRecommendDTO {
    private Integer id;
    private String name;
    private BigDecimal price;
    private BigDecimal discountPrice;
    private Double rating;
    private Long totalReviews;
    private String imageUrls;
    private Integer shopId;
    private String shopName;
    private String categoryName;
    private String recommendReason; // "Vì bạn hay mua phở", "Được đánh giá cao"...
}