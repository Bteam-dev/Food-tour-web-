package com.example.FoodTourApp.DTO.ProductVariantDTO;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class CreateVariantRequestDTO {
    @NotBlank(message = "Variant name is required")
    private String variantName;

    @NotBlank(message = "Variant value is required")
    private String variantValue;

    @NotNull(message = "Price adjustment is required")
    private Double priceAdjustment = 0.0;
}
