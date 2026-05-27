package com.example.FoodTourApp.DTO.ProductVariantDTO;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateVariantRequestDTO {
    private Integer variantTypeId; // ID của loại biến thể
    private String variantValue; // Giá trị cụ thể
    private BigDecimal priceAdjustment;
    private Boolean isActive;
}
