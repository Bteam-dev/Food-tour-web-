package com.example.FoodTourApp.DTO.ProductVariantDTO;

import lombok.Data;

@Data
public class VariantResponseDTO {
    private Integer id;
    private String variantName;
    private String variantValue;
    private Double priceAdjustment;
    private Boolean isActive;
}
