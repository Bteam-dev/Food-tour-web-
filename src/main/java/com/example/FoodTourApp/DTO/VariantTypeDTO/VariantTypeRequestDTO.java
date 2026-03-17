package com.example.FoodTourApp.DTO.VariantTypeDTO;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

@Data
public class VariantTypeRequestDTO {
    @NotBlank(message = "Tên loại biến thể không được để trống")
    private String name; // Tên loại biến thể: "Topping", "Size", "Màu sắc"

    private String description; // Mô tả

    /**
     * Kiểu chọn: SINGLE (chỉ chọn 1, ví dụ Size) hoặc MULTIPLE (chọn nhiều, ví dụ Topping).
     * Mặc định MULTIPLE nếu không truyền.
     */
    @Pattern(regexp = "SINGLE|MULTIPLE", message = "selectionType phải là SINGLE hoặc MULTIPLE")
    private String selectionType;
}

