package com.example.FoodTourApp.service;

import com.example.FoodTourApp.DTO.ProductDTO.ProductResponseDTO;

import java.util.List;

public interface RecommendationService {

    List<ProductResponseDTO> getForYou(Integer userId, int limit);

    List<ProductResponseDTO> getSimilar(Integer productId, int limit);
}
