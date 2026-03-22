package com.example.FoodTourApp.service;

import com.example.FoodTourApp.DTO.RecommendDTO.ProductRecommendDTO;

import java.util.List;

public interface RecommendationService {

    List<ProductRecommendDTO> getForYou(Integer userId, int limit);

    List<ProductRecommendDTO> getSimilar(Integer productId, int limit);
}
