package com.example.FoodTourApp.DTO.ProductVariantDTO;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class CreateVariantRequestDTO {
    @NotNull(message = "Variant type ID is required")
    private Integer variantTypeId; // ID của loại biến thể (Topping, Size, ...)

    @NotBlank(message = "Variant value is required")
    private String variantValue; // Giá trị cụ thể: "Phô mai", "Lớn", ...

    @NotNull(message = "Price adjustment is required")
    private Double priceAdjustment = 0.0;
}
