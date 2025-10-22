package com.example.FoodTourApp.DTO.ProductDTO;

import lombok.Data;

import java.util.List;

@Data
public class UpdateProductRequestDTO {
    private String name;
    private String description;
    private Double price;
    private Double discountPrice;
    private List<String> imageUrls;
    private String ingredients;
    private String nutritionInfo;
    private Integer preparationTime;
    private Boolean isAvailable;
    private Integer stockQuantity;
    private Integer minOrderQuantity;
    private Integer maxOrderQuantity;
    private List<String> tags;
    private Integer categoryId;
}
