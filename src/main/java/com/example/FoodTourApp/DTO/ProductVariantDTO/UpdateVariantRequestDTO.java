package com.example.FoodTourApp.DTO.ProductVariantDTO;

import lombok.Data;

@Data
public class UpdateVariantRequestDTO {
    private String variantName;
    private String variantValue;
    private Double priceAdjustment;
    private Boolean isActive;
}
