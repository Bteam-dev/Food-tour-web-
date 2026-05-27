package com.example.FoodTourApp.DTO.ProductVariantDTO;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class CreateVariantRequestDTO {
    @NotNull(message = "Variant type ID is required")
    private Integer variantTypeId; // ID của loại biến thể (Topping, Size, ...)

    @NotBlank(message = "Variant value is required")
    private String variantValue; // Giá trị cụ thể: "Phô mai", "Lớn", ...

    @NotNull(message = "Price adjustment is required")
    private BigDecimal priceAdjustment = BigDecimal.ZERO;
}
