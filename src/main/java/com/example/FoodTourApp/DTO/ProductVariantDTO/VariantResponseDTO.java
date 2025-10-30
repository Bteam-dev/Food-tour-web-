package com.example.FoodTourApp.DTO.ProductVariantDTO;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class VariantResponseDTO {
    private Integer id;
    private Integer variantTypeId; // ID của loại biến thể
    private String variantTypeName; // Tên loại biến thể: "Topping", "Size", ...
    private String variantValue; // Giá trị cụ thể: "Phô mai", "Lớn", ...
    private BigDecimal priceAdjustment;
    private Boolean isActive;
}
