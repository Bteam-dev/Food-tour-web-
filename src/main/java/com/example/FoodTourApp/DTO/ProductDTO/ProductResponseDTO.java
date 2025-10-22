package com.example.FoodTourApp.DTO.ProductDTO;

import com.example.FoodTourApp.DTO.ProductVariantDTO.VariantResponseDTO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProductResponseDTO {
    private Integer id;
    private Integer shopId;
    private String shopName;
    private Integer categoryId;
    private String categoryName;
    private String name;
    private String description;
    private Double price;
    private Double discountPrice;
    private List<String> imageUrls;
    private String ingredients;
    private String nutritionInfo;
    private Integer preparationTime;
    private Boolean isAvailable;
    private Integer stockQuantity;
    private Integer minOrderQuantity;
    private Integer maxOrderQuantity;
    private Double rating;
    private Integer totalReviews;
    private List<String> tags;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<VariantResponseDTO> variants;
}
