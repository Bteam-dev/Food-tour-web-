package com.example.FoodTourApp.DTO.ProductVariantDTO;

import lombok.Data;

import java.math.BigDecimal;

/**
 * DTO dùng để cập nhật variants trong UpdateProductRequestDTO
 * Có thể dùng để thêm mới, cập nhật hoặc xóa variants
 */
@Data
public class ProductVariantUpdateDTO {
    private Integer id; // null = thêm mới, có giá trị = cập nhật
    private Integer variantTypeId;
    private String variantValue;
    private BigDecimal priceAdjustment;
    private Boolean isActive;
    private Boolean shouldDelete; // true = xóa variant này
}

