package com.example.FoodTourApp.service;

import java.util.Map;

/**
 * Service export training data cho recommendation model
 */
public interface RecommendationDataExportService {
    
    /**
     * Export toàn bộ data cần thiết để train model
     * Bao gồm: interactions, products, users, metadata
     */
    Map<String, Object> exportAllTrainingData();
}
