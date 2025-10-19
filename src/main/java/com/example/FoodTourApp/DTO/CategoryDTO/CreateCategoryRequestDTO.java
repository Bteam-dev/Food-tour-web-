package com.example.FoodTourApp.DTO.CategoryDTO;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * DTO cho request tạo Category
 */
@Data
public class CreateCategoryRequestDTO {

    @NotBlank(message = "Tên danh mục không được để trống")
    private String name;

    private String description;

    private Integer parentId;

    private String imageUrl;

    private Integer sortOrder = 0;
}
