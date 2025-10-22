package com.example.FoodTourApp.DTO.VariantTypeDTO;

import lombok.Data;

@Data
public class VariantTypeRequestDTO {
    private String name; // Tên loại biến thể: "Topping", "Size", "Màu sắc"
    private String description; // Mô tả
}

