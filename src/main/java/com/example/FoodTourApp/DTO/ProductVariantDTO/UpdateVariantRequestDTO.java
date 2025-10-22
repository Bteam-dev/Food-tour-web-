package com.example.FoodTourApp.DTO.ProductVariantDTO;

import lombok.Data;

@Data
public class UpdateVariantRequestDTO {
    private Integer variantTypeId; // ID của loại biến thể
    private String variantValue; // Giá trị cụ thể
    private Double priceAdjustment;
    private Boolean isActive;
}
