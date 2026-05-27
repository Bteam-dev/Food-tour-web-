package com.example.FoodTourApp.DTO.ProductDTO;

import com.example.FoodTourApp.DTO.ProductVariantDTO.CreateVariantRequestDTO;
import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateProductRequestDTO {
    @NotBlank(message = "Name is required")
    private String name;

    private String description;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.00", message = "Price must be non-negative")
    private BigDecimal price;

    private BigDecimal discountPrice;

    private List<String> imageUrls;

    private List<String> ingredients; // Đổi từ String sang List<String>

    private List<String> nutritionInfo; // Đổi từ Map sang List<String> (JSON array)

    private Integer preparationTime;

    private Integer stockQuantity = 0;

    private Integer minOrderQuantity;

    private Integer maxOrderQuantity;

    private List<String> tags;

    private List<CreateVariantRequestDTO> variants;

    @NotNull(message = "Category ID is required")
    private Integer categoryId;

    private Integer shopId;
}