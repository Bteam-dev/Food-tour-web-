package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.entity.Product;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.entity.UserBehavior;
import com.example.FoodTourApp.repository.ProductRepository;
import com.example.FoodTourApp.repository.UserBehaviorRepository;
import com.example.FoodTourApp.repository.UserRepository;
import com.example.FoodTourApp.service.RecommendationDataExportService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationDataExportServiceImpl implements RecommendationDataExportService {

    private final UserBehaviorRepository behaviorRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    public Map<String, Object> exportAllTrainingData() {
        log.info("Exporting training data...");
        
        Map<String, Object> result = new HashMap<>();
        
        // 1. Export interactions (từ user_behaviors table)
        result.put("interactions", exportInteractions());
        
        // 2. Export products (với text features)
        result.put("products", exportProducts());
        
        // 3. Export users
        result.put("users", exportUsers());
        
        // 4. Metadata
        result.put("metadata", buildMetadata());
        
        log.info("Export completed");
        return result;
    }

    private List<Map<String, Object>> exportInteractions() {
        LocalDateTime since = LocalDateTime.now().minusMonths(6);  // 6 tháng gần nhất
        List<UserBehavior> behaviors = behaviorRepository.findAllForTraining(since);
        
        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        
        return behaviors.stream().map(b -> {
            Map<String, Object> m = new HashMap<>();
            m.put("user_id", b.getUser().getId());
            m.put("product_id", b.getProduct().getId());
            m.put("action_type", b.getActionType().name());
            m.put("weight", b.getWeight());
            m.put("rating", b.getRating());
            m.put("quantity", b.getQuantity());
            m.put("view_duration", b.getViewDurationSeconds());
            m.put("category_id", b.getCategoryId());
            m.put("shop_id", b.getShopId());
            m.put("session_id", b.getSessionId());
            m.put("source", b.getSource() != null ? b.getSource().name() : null);
            m.put("timestamp", b.getCreatedAt().format(fmt));
            return m;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> exportProducts() {
        List<Product> products = productRepository.findAll();
        
        return products.stream()
                .filter(p -> p.getIsAvailable() != null && p.getIsAvailable())
                .map(p -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("product_id", p.getId());
                    m.put("name", p.getName());
                    m.put("description", p.getDescription());
                    m.put("category_id", p.getCategory().getId());
                    m.put("category_name", p.getCategory().getName());
                    m.put("shop_id", p.getShop().getId());
                    m.put("price", p.getPrice());
                    m.put("discount_price", p.getDiscountPrice());
                    m.put("rating", p.getRating());
                    m.put("total_reviews", p.getTotalReviews());
                    
                    // Parse JSON fields
                    m.put("ingredients", parseJsonArray(p.getIngredients()));
                    m.put("tags", parseJsonArray(p.getTags()));
                    m.put("nutrition_info", parseJsonArray(p.getNutritionInfo()));
                    
                    return m;
                }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> exportUsers() {
        List<User> users = userRepository.findAll();
        
        return users.stream()
                .filter(u -> u.getIsActive() != null && u.getIsActive())
                .map(u -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("user_id", u.getId());
                    m.put("gender", u.getGender() != null ? u.getGender().name() : null);
                    // Không export thông tin nhạy cảm
                    return m;
                }).collect(Collectors.toList());
    }

    private Map<String, Object> buildMetadata() {
        Map<String, Object> meta = new HashMap<>();
        meta.put("exported_at", LocalDateTime.now().toString());
        meta.put("total_interactions", behaviorRepository.count());
        meta.put("total_products", productRepository.count());
        meta.put("total_users", userRepository.count());
        meta.put("unique_users_with_behavior", behaviorRepository.countDistinctUsers());
        meta.put("unique_products_with_behavior", behaviorRepository.countDistinctProducts());
        return meta;
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isEmpty()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, List.class);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
