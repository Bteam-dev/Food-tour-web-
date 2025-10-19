package com.example.FoodTourApp.DTO.CategoryDTO;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO cho response Category
 */
@Data
public class CategoryResponseDTO {

    private Integer id;

    private String name;

    private String description;

    private Integer parentId;

    private String parentName;

    private String imageUrl;

    private Boolean isActive;

    private Integer sortOrder;

    private LocalDateTime createdAt;

    // Danh sách category con (nếu có)
    private List<CategoryResponseDTO> children;

    // Số lượng sản phẩm trong category (optional)
    private Long productCount;
}

