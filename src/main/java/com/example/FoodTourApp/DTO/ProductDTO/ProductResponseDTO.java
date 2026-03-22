package com.example.FoodTourApp.DTO.ProductDTO;

import com.example.FoodTourApp.DTO.ProductVariantDTO.VariantResponseDTO;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class ProductResponseDTO {
    private Integer id;
    private Integer shopId;
    private String shopName;
    private Boolean shopIsOpen; // ✅ NEW: shop's open/closed status
    private Integer categoryId;
    private String categoryName;
    private String name;
    private String description;
    private BigDecimal price;
    private BigDecimal discountPrice;
    private List<String> imageUrls;
    private List<String> ingredients; // Đổi từ String sang List<String>
    private Map<String, Object> nutritionInfo; // Đổi từ String sang Map (JSON object)
    private Integer preparationTime;
    private Boolean isAvailable;
    private Integer stockQuantity;
    private Integer minOrderQuantity;
    private Integer maxOrderQuantity;
    private Double rating;
    private Long totalReviews;
    private List<String> tags;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<VariantResponseDTO> variants;
}
