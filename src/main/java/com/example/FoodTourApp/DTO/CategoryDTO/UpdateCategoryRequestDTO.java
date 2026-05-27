package com.example.FoodTourApp.DTO.CategoryDTO;

import lombok.Data;

/**
 * DTO cho request cập nhật Category
 */
@Data
public class UpdateCategoryRequestDTO {

    private String name;

    private String description;

    private Integer parentId;

    private String imageUrl;

    private Integer sortOrder;

    private Boolean isActive;
}

