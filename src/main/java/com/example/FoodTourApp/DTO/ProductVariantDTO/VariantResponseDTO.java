package com.example.FoodTourApp.DTO.ProductVariantDTO;

import lombok.Data;

@Data
public class VariantResponseDTO {
    private Integer id;
    private Integer variantTypeId; // ID của loại biến thể
    private String variantTypeName; // Tên loại biến thể: "Topping", "Size", ...
    private String variantValue; // Giá trị cụ thể: "Phô mai", "Lớn", ...
    private Double priceAdjustment;
    private Boolean isActive;
}
