package com.example.FoodTourApp.DTO.ProductDTO;

import com.example.FoodTourApp.DTO.ProductVariantDTO.ProductVariantUpdateDTO;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class UpdateProductRequestDTO {
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
    private List<String> tags;
    private Integer categoryId;

    // Thêm trường để quản lý variants
    private List<ProductVariantUpdateDTO> variants;
}
