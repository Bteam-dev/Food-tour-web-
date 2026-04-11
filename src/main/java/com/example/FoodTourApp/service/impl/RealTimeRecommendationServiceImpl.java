package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.DTO.ProductDTO.ProductResponseDTO;
import com.example.FoodTourApp.entity.Product;
import com.example.FoodTourApp.entity.UserBehavior;
import com.example.FoodTourApp.entity.UserBehavior.ActionType;
import com.example.FoodTourApp.entity.UserBehavior.BehaviorSource;
import com.example.FoodTourApp.repository.ProductRepository;
import com.example.FoodTourApp.repository.UserBehaviorRepository;
import com.example.FoodTourApp.repository.UserRepository;
import com.example.FoodTourApp.service.ProductService;
import com.example.FoodTourApp.service.RealTimeRecommendationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Real-time Recommendation Service - Kiểu YouTube/Shopee
 * 
 * Kiến trúc:
 * 1. Product Embeddings: Precomputed từ Colab, lưu trong ES
 * 2. User Embeddings: Tính online từ weighted average của product embeddings
 * 3. Redis Cache: Lưu user embedding để không phải tính lại mỗi request
 * 4. ES KNN Search: Tìm products gần nhất với user embedding
 * 
 * Real-time update flow:
 * User action → trackBehavior() → updateUserEmbedding() → Redis cache
 * Next request → getRecommendations() → dùng cached embedding → ES KNN
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RealTimeRecommendationServiceImpl implements RealTimeRecommendationService {

    private final UserBehaviorRepository behaviorRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ProductService productService;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisObjectTemplate;

    // ES Configuration
    private static final String ES_URL = "http://localhost:9200";
    private static final String ES_INDEX = "products_recommend";
    
    // Redis Keys
    private static final String USER_EMBEDDING_KEY = "rec:user:embedding:";
    private static final String USER_PREFS_KEY = "rec:user:prefs:";
    private static final Duration EMBEDDING_TTL = Duration.ofHours(6);
    
    // Preloaded data
    private Map<Integer, float[]> productEmbeddings = new HashMap<>();
    private Map<Integer, Integer> productShopMap = new HashMap<>();
    private Map<Integer, Integer> productCategoryMap = new HashMap<>();
    private int embeddingDim = 64;  // Updated: Two-Tower model outputs 64-dim embeddings

    @PostConstruct
    public void init() {
        loadProductEmbeddings();
        loadProductMetadata();
    }

    private void loadProductEmbeddings() {
        try {
            var resource = new ClassPathResource("models/FoodRecommendSearchByBehavior/model_output/product_embeddings.json");
            if (resource.exists()) {
                Map<String, List<Double>> raw = objectMapper.readValue(
                        resource.getInputStream(),
                        new TypeReference<>() {}
                );
                raw.forEach((k, v) -> {
                    float[] arr = new float[v.size()];
                    for (int i = 0; i < v.size(); i++) {
                        arr[i] = v.get(i).floatValue();
                    }
                    productEmbeddings.put(Integer.parseInt(k), arr);
                });
                if (!productEmbeddings.isEmpty()) {
                    embeddingDim = productEmbeddings.values().iterator().next().length;
                }
                log.info("✅ Loaded {} product embeddings (dim={})", productEmbeddings.size(), embeddingDim);
            } else {
                log.warn("⚠️ product_embeddings.json not found, will use ES only");
            }
        } catch (Exception e) {
            log.warn("⚠️ Failed to load product embeddings: {}", e.getMessage());
        }
    }

    private void loadProductMetadata() {
        try {
            List<Product> products = productRepository.findAll();
            for (Product p : products) {
                productShopMap.put(p.getId(), p.getShop().getId());
                productCategoryMap.put(p.getId(), p.getCategory().getId());
            }
            log.info("✅ Loaded metadata for {} products", products.size());
        } catch (Exception e) {
            log.warn("⚠️ Failed to load product metadata: {}", e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BEHAVIOR TRACKING
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public void trackBehavior(Integer userId, Integer productId, 
                              ActionType actionType,
                              String sessionId,
                              BehaviorSource source,
                              Integer viewDurationSeconds,
                              Integer quantity,
                              Integer rating) {
        try {
            Product product = productRepository.findById(productId).orElse(null);
            if (product == null) {
                log.warn("Product {} not found for tracking", productId);
                return;
            }

            UserBehavior behavior = UserBehavior.builder()
                    .user(userRepository.getReferenceById(userId))
                    .product(product)
                    .actionType(actionType)
                    .weight(calculateWeight(actionType, viewDurationSeconds, quantity, rating))
                    .sessionId(sessionId)
                    .source(source)
                    .viewDurationSeconds(viewDurationSeconds)
                    .quantity(quantity)
                    .rating(rating)
                    .categoryId(product.getCategory().getId())
                    .shopId(product.getShop().getId())
                    .build();

            behaviorRepository.save(behavior);
            log.debug("Tracked {} for user {} on product {}", actionType, userId, productId);

            // Async update user embedding
            updateUserEmbeddingAsync(userId);

        } catch (Exception e) {
            log.error("Failed to track behavior: {}", e.getMessage());
        }
    }

    private double calculateWeight(ActionType actionType, Integer duration, Integer quantity, Integer rating) {
        double baseWeight = actionType.getDefaultWeight();
        
        // Adjust weight based on context
        if (actionType == ActionType.VIEW && duration != null) {
            // View > 30s: bonus weight
            if (duration > 30) baseWeight *= 1.5;
            else if (duration > 60) baseWeight *= 2.0;
        }
        
        if (actionType == ActionType.PURCHASE && quantity != null) {
            // Multiple purchases: bonus
            baseWeight *= Math.min(quantity, 3);
        }
        
        if ((actionType == ActionType.REVIEW_POSITIVE || actionType == ActionType.REVIEW_NEGATIVE) && rating != null) {
            // 5 star: extra positive, 1 star: extra negative
            if (rating == 5) baseWeight *= 1.5;
            else if (rating == 1) baseWeight *= 1.5;
        }
        
        return baseWeight;
    }

    @Override
    public void trackView(Integer userId, Integer productId, String sessionId, 
                          BehaviorSource source, Integer durationSeconds) {
        trackBehavior(userId, productId, ActionType.VIEW, sessionId, source, durationSeconds, null, null);
    }

    @Override
    public void trackClick(Integer userId, Integer productId, String sessionId, BehaviorSource source) {
        trackBehavior(userId, productId, ActionType.CLICK, sessionId, source, null, null, null);
    }

    @Override
    public void trackSearchClick(Integer userId, Integer productId, String sessionId) {
        // Use SEARCH_CLICK action type for higher weight (2.0) and SEARCH source
        trackBehavior(userId, productId, ActionType.SEARCH_CLICK, sessionId, BehaviorSource.SEARCH, null, null, null);
    }

    @Override
    public void trackRecommendClick(Integer userId, Integer productId, String sessionId) {
        // Use RECOMMEND_CLICK action type for highest click weight (2.5) and RECOMMENDATION source
        trackBehavior(userId, productId, ActionType.RECOMMEND_CLICK, sessionId, BehaviorSource.RECOMMENDATION, null, null, null);
    }

    @Override
    public void trackHomeClick(Integer userId, Integer productId, String sessionId) {
        trackBehavior(userId, productId, ActionType.CLICK, sessionId, BehaviorSource.HOME, null, null, null);
    }

    @Override
    public void trackCategoryClick(Integer userId, Integer productId, String sessionId) {
        trackBehavior(userId, productId, ActionType.CLICK, sessionId, BehaviorSource.CATEGORY, null, null, null);
    }

    @Override
    public void trackSimilarClick(Integer userId, Integer productId, String sessionId) {
        trackBehavior(userId, productId, ActionType.CLICK, sessionId, BehaviorSource.SIMILAR, null, null, null);
    }

    @Override
    public void trackAddCart(Integer userId, Integer productId, String sessionId, Integer quantity) {
        trackBehavior(userId, productId, ActionType.ADD_CART, sessionId, BehaviorSource.DIRECT, null, quantity, null);
    }

    @Override
    public void trackRemoveCart(Integer userId, Integer productId, String sessionId) {
        trackBehavior(userId, productId, ActionType.REMOVE_CART, sessionId, BehaviorSource.DIRECT, null, null, null);
    }

    @Override
    public void trackWishlistAdd(Integer userId, Integer productId, String sessionId) {
        trackBehavior(userId, productId, ActionType.WISHLIST_ADD, sessionId, BehaviorSource.DIRECT, null, null, null);
    }

    @Override
    public void trackWishlistRemove(Integer userId, Integer productId, String sessionId) {
        trackBehavior(userId, productId, ActionType.WISHLIST_REMOVE, sessionId, BehaviorSource.DIRECT, null, null, null);
    }

    @Override
    public void trackPurchase(Integer userId, Integer productId, Integer quantity) {
        trackBehavior(userId, productId, ActionType.PURCHASE, null, BehaviorSource.DIRECT, null, quantity, null);
    }

    @Override
    public void trackReview(Integer userId, Integer productId, Integer rating) {
        ActionType type = rating >= 4 ? ActionType.REVIEW_POSITIVE : ActionType.REVIEW_NEGATIVE;
        trackBehavior(userId, productId, type, null, BehaviorSource.DIRECT, null, null, rating);
    }

    @Override
    public void updateViewDuration(com.example.FoodTourApp.entity.User user, Integer productId, String sessionId, Integer durationSeconds) {
        try {
            Integer userId = user != null ? user.getId() : null;
            
            // Tìm VIEW record gần đây nhất cho user/session + product
            UserBehavior existingView = behaviorRepository.findLatestViewBehavior(
                userId, productId, sessionId, ActionType.VIEW
            );
            
            if (existingView != null) {
                // Update duration cho existing record
                existingView.setViewDurationSeconds(durationSeconds);
                behaviorRepository.save(existingView);
                
                log.info("Updated view duration: userId={}, productId={}, sessionId={}, duration={}s",
                    userId, productId, sessionId, durationSeconds);
                
                // Update user embedding after behavior change
                if (userId != null) {
                    updateUserEmbedding(userId);
                }
            } else {
                log.warn("No existing VIEW record found to update duration: userId={}, productId={}, sessionId={}",
                    userId, productId, sessionId);
            }
        } catch (Exception e) {
            log.error("Failed to update view duration: userId={}, productId={}, sessionId={}, duration={}, error={}",
                user != null ? user.getId() : null, productId, sessionId, durationSeconds, e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // USER EMBEDDING MANAGEMENT
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    @Async
    public void updateUserEmbedding(Integer userId) {
        updateUserEmbeddingAsync(userId);
    }

    private void updateUserEmbeddingAsync(Integer userId) {
        try {
            float[] userEmb = computeUserEmbedding(userId);
            if (userEmb != null) {
                // Cache in Redis
                String key = USER_EMBEDDING_KEY + userId;
                redisObjectTemplate.opsForValue().set(key, userEmb, EMBEDDING_TTL);
                log.debug("Updated embedding cache for user {}", userId);
            }
        } catch (Exception e) {
            log.error("Failed to update user embedding: {}", e.getMessage());
        }
    }

    /**
     * Tính user embedding = weighted average của product embeddings đã tương tác
     * 
     * Formula: user_emb = Σ(weight_i * product_emb_i) / Σ(weight_i)
     * 
     * Chỉ dùng behaviors trong 30 ngày gần nhất với time decay
     */
    private float[] computeUserEmbedding(Integer userId) {
        LocalDateTime since = LocalDateTime.now().minusDays(30);
        List<Object[]> interactions = behaviorRepository.findUserProductWeights(userId);
        
        if (interactions.isEmpty()) {
            return null;
        }

        float[] embedding = new float[embeddingDim];
        double totalWeight = 0;
        LocalDateTime now = LocalDateTime.now();

        for (Object[] row : interactions) {
            Integer productId = (Integer) row[0];
            Double weight = ((Number) row[1]).doubleValue();
            
            if (weight <= 0) continue;
            
            float[] productEmb = productEmbeddings.get(productId);
            if (productEmb == null) continue;

            // Time decay: recent actions matter more
            // decay = 0.95^(days_ago)
            // Simplified: assume all in last 30 days, no decay for now
            
            for (int i = 0; i < embeddingDim; i++) {
                embedding[i] += productEmb[i] * weight.floatValue();
            }
            totalWeight += weight;
        }

        if (totalWeight == 0) return null;

        // Normalize
        for (int i = 0; i < embeddingDim; i++) {
            embedding[i] /= (float) totalWeight;
        }

        // L2 normalize
        float norm = 0;
        for (float v : embedding) norm += v * v;
        norm = (float) Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < embeddingDim; i++) {
                embedding[i] /= norm;
            }
        }

        return embedding;
    }

    private float[] getUserEmbedding(Integer userId) {
        // Try Redis cache first
        String key = USER_EMBEDDING_KEY + userId;
        Object cached = redisObjectTemplate.opsForValue().get(key);
        if (cached != null) {
            if (cached instanceof float[]) {
                return (float[]) cached;
            } else if (cached instanceof List) {
                // Redis might deserialize as List
                List<?> list = (List<?>) cached;
                float[] arr = new float[list.size()];
                for (int i = 0; i < list.size(); i++) {
                    arr[i] = ((Number) list.get(i)).floatValue();
                }
                return arr;
            }
        }

        // Compute and cache
        float[] emb = computeUserEmbedding(userId);
        if (emb != null) {
            redisObjectTemplate.opsForValue().set(key, emb, EMBEDDING_TTL);
        }
        return emb;
    }

    @Override
    public void invalidateUserEmbedding(Integer userId) {
        String key = USER_EMBEDDING_KEY + userId;
        redisObjectTemplate.delete(key);
    }

    /**
     * Scheduled job: Precompute user embeddings mỗi 6 tiếng
     * Giúp warm up cache và cải thiện response time cho lần request đầu
     */
    @Scheduled(cron = "0 0 */6 * * *") // Chạy mỗi 6 tiếng: 0h, 6h, 12h, 18h
    public void scheduledPrecomputeEmbeddings() {
        precomputeAllUserEmbeddings();
    }

    @Override
    public void precomputeAllUserEmbeddings() {
        log.info("Starting precompute user embeddings job...");
        try {
            // Lấy danh sách unique user IDs có hành vi trong 30 ngày gần nhất
            LocalDateTime since = LocalDateTime.now().minusDays(30);
            List<UserBehavior> recentBehaviors = behaviorRepository.findAllForTraining(since);
            List<Integer> userIds = recentBehaviors.stream()
                    .map(b -> b.getUser().getId())
                    .distinct()
                    .collect(Collectors.toList());
            
            int processed = 0;
            for (Integer userId : userIds) {
                try {
                    float[] emb = computeUserEmbedding(userId);
                    if (emb != null) {
                        String key = USER_EMBEDDING_KEY + userId;
                        redisObjectTemplate.opsForValue().set(key, emb, EMBEDDING_TTL);
                        processed++;
                    }
                } catch (Exception e) {
                    log.warn("Failed to compute embedding for user {}: {}", userId, e.getMessage());
                }
            }
            
            log.info("✅ Precomputed embeddings for {}/{} users", processed, userIds.size());
        } catch (Exception e) {
            log.error("Failed to precompute user embeddings: {}", e.getMessage(), e);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // REAL-TIME RECOMMENDATIONS
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public List<ProductResponseDTO> getRecommendations(Integer userId, String sessionId, int limit) {
        Set<Integer> excludeIds = new HashSet<>();
        List<Integer> resultIds = new ArrayList<>();

        // 1. Get products to exclude (already interacted with negative feedback)
        List<Integer> negativeProducts = behaviorRepository.findNegativeProductIds(userId);
        excludeIds.addAll(negativeProducts);
        
        // Also exclude products recently viewed (để tránh spam cùng 1 sản phẩm)
        LocalDateTime recentSince = LocalDateTime.now().minusHours(24);
        List<Integer> recentPositive = behaviorRepository.findRecentPositiveProductIds(userId, recentSince);
        // Chỉ exclude nếu đã xem nhiều lần (tránh gợi ý lại ngay)
        excludeIds.addAll(recentPositive);

        // 2. Try user embedding approach (KNN search)
        float[] userEmb = getUserEmbedding(userId);
        if (userEmb != null) {
            List<Integer> knnResults = searchByEmbedding(userEmb, excludeIds, limit * 2);
            resultIds.addAll(knnResults);
            log.debug("KNN found {} products for user {}", knnResults.size(), userId);
        }

        // 3. Session-based boost: recent views in this session
        if (sessionId != null && resultIds.size() < limit) {
            List<Integer> sessionProducts = behaviorRepository.findProductIdsBySessionId(sessionId);
            for (Integer pid : sessionProducts) {
                if (!excludeIds.contains(pid)) {
                    List<Integer> similar = getSimilarProductIds(pid, 5);
                    for (Integer simId : similar) {
                        if (!excludeIds.contains(simId) && !resultIds.contains(simId)) {
                            resultIds.add(simId);
                        }
                    }
                }
            }
        }

        // 4. Category-based recommendations (sử dụng findUserCategoryPreferences)
        if (resultIds.size() < limit) {
            List<Object[]> categoryPrefs = behaviorRepository.findUserCategoryPreferences(userId, PageRequest.of(0, 3));
            for (Object[] row : categoryPrefs) {
                Integer categoryId = (Integer) row[0];
                List<Product> categoryProducts = productRepository.findByCategoryIdAndIsAvailableTrue(categoryId, PageRequest.of(0, 20));
                for (Product p : categoryProducts) {
                    if (!excludeIds.contains(p.getId()) && !resultIds.contains(p.getId())) {
                        resultIds.add(p.getId());
                        if (resultIds.size() >= limit * 2) break;
                    }
                }
            }
        }

        // 5. Shop-based recommendations (sử dụng findUserShopPreferences)
        if (resultIds.size() < limit) {
            List<Object[]> shopPrefs = behaviorRepository.findUserShopPreferences(userId, PageRequest.of(0, 3));
            for (Object[] row : shopPrefs) {
                Integer shopId = (Integer) row[0];
                List<Product> shopProducts = productRepository.findByShopIdAndIsAvailableTrue(shopId, PageRequest.of(0, 20));
                for (Product p : shopProducts) {
                    if (!excludeIds.contains(p.getId()) && !resultIds.contains(p.getId())) {
                        resultIds.add(p.getId());
                        if (resultIds.size() >= limit * 2) break;
                    }
                }
            }
        }

        // 6. Collaborative filtering: what similar users bought
        if (resultIds.size() < limit) {
            List<Integer> purchasedIds = behaviorRepository.findRecentByUserId(userId, PageRequest.of(0, 10))
                    .stream()
                    .filter(b -> b.getActionType() == ActionType.PURCHASE)
                    .map(b -> b.getProduct().getId())
                    .collect(Collectors.toList());

            if (!purchasedIds.isEmpty()) {
                List<Integer> similarUsers = behaviorRepository.findSimilarUsers(userId, purchasedIds);
                if (!similarUsers.isEmpty()) {
                    List<Integer> collabIds = behaviorRepository
                            .findProductsFromSimilarUsers(
                                    similarUsers.subList(0, Math.min(10, similarUsers.size())),
                                    new ArrayList<>(excludeIds),
                                    PageRequest.of(0, limit))
                            .stream()
                            .map(row -> (Integer) row[0])
                            .collect(Collectors.toList());
                    
                    for (Integer id : collabIds) {
                        if (!resultIds.contains(id)) {
                            resultIds.add(id);
                        }
                    }
                }
            }
        }

        // 7. Fallback: top rated products
        if (resultIds.size() < limit) {
            List<Product> topRated = productRepository.findTopRatedAvailable(limit * 2);
            for (Product p : topRated) {
                if (!excludeIds.contains(p.getId()) && !resultIds.contains(p.getId())) {
                    resultIds.add(p.getId());
                }
            }
        }

        // 8. Apply diversity: max 2 products per shop
        resultIds = applyDiversity(resultIds, limit);

        // 9. Convert to DTOs
        return convertToProductDTOs(resultIds.subList(0, Math.min(limit, resultIds.size())));
    }

    @Override
    public List<ProductResponseDTO> getSessionRecommendations(String sessionId, int limit) {
        List<Integer> sessionProducts = behaviorRepository.findProductIdsBySessionId(sessionId);
        
        if (sessionProducts.isEmpty()) {
            // Cold start: return top rated
            return productRepository.findTopRatedAvailable(limit).stream()
                    .map(p -> productService.getProductById(p.getId()))
                    .collect(Collectors.toList());
        }

        // Find similar products to session items
        Set<Integer> resultIds = new LinkedHashSet<>();
        Set<Integer> excludeIds = new HashSet<>(sessionProducts);

        for (Integer pid : sessionProducts) {
            List<Integer> similar = getSimilarProductIds(pid, 5);
            for (Integer simId : similar) {
                if (!excludeIds.contains(simId)) {
                    resultIds.add(simId);
                }
            }
        }

        List<Integer> finalList = applyDiversity(new ArrayList<>(resultIds), limit);
        return convertToProductDTOs(finalList);
    }

    @Override
    public List<ProductResponseDTO> getSimilarProducts(Integer productId, int limit) {
        List<Integer> similarIds = getSimilarProductIds(productId, limit);
        return convertToProductDTOs(similarIds);
    }

    @Override
    public List<ProductResponseDTO> getBecauseYouViewed(Integer userId, Integer viewedProductId, int limit) {
        Set<Integer> excludeIds = new HashSet<>();
        excludeIds.add(viewedProductId);
        
        // Add user's interacted products
        behaviorRepository.findRecentByUserId(userId, PageRequest.of(0, 50))
                .forEach(b -> excludeIds.add(b.getProduct().getId()));

        List<Integer> similarIds = getSimilarProductIds(viewedProductId, limit + excludeIds.size());
        similarIds = similarIds.stream()
                .filter(id -> !excludeIds.contains(id))
                .limit(limit)
                .collect(Collectors.toList());

        return convertToProductDTOs(similarIds);
    }

    @Override
    public List<ProductResponseDTO> getAlsoBought(Integer productId, int limit) {
        // Find users who bought this product
        List<Integer> buyers = behaviorRepository.findSimilarUsers(0, List.of(productId));
        
        if (buyers.isEmpty()) {
            return getSimilarProducts(productId, limit);
        }

        // Find what else they bought
        List<Object[]> alsoBought = behaviorRepository.findProductsFromSimilarUsers(
                buyers.subList(0, Math.min(50, buyers.size())),
                List.of(productId),
                PageRequest.of(0, limit));

        List<Integer> productIds = alsoBought.stream()
                .map(row -> (Integer) row[0])
                .collect(Collectors.toList());

        return convertToProductDTOs(productIds);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ══════════════════════════════════════════════════════════════════════════

    private List<Integer> searchByEmbedding(float[] embedding, Set<Integer> excludeIds, int k) {
        try {
            RestTemplate rt = new RestTemplate();
            
            Map<String, Object> mustNot = Map.of(
                    "terms", Map.of("product_id", new ArrayList<>(excludeIds))
            );

            Map<String, Object> knn = new LinkedHashMap<>();
            knn.put("field", "embedding");
            knn.put("query_vector", toList(embedding));
            knn.put("k", k);
            knn.put("num_candidates", k * 3);
            knn.put("filter", Map.of("bool", Map.of("must_not", mustNot)));

            var resp = rt.postForEntity(
                    ES_URL + "/" + ES_INDEX + "/_search",
                    Map.of("knn", knn, "size", k),
                    Map.class
            );

            if (resp.getBody() == null) return List.of();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> hits = (List<Map<String, Object>>)
                    ((Map<String, Object>) resp.getBody().get("hits")).get("hits");

            return hits.stream()
                    .map(h -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> source = (Map<String, Object>) h.get("_source");
                        return (Integer) source.get("product_id");
                    })
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("ES KNN search error: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Integer> getSimilarProductIds(Integer productId, int limit) {
        float[] embedding = productEmbeddings.get(productId);
        if (embedding != null) {
            return searchByEmbedding(embedding, Set.of(productId), limit);
        }
        
        // Fallback: same category
        return productRepository.findSimilarByCategory(productId, limit).stream()
                .map(Product::getId)
                .collect(Collectors.toList());
    }

    private List<Integer> applyDiversity(List<Integer> productIds, int limit) {
        List<Integer> result = new ArrayList<>();
        Map<Integer, Integer> shopCount = new HashMap<>();

        for (Integer pid : productIds) {
            if (result.size() >= limit) break;

            Integer shopId = productShopMap.getOrDefault(pid, -1);
            int count = shopCount.getOrDefault(shopId, 0);
            
            if (count < 2) {  // Max 2 per shop
                result.add(pid);
                shopCount.put(shopId, count + 1);
            }
        }

        return result;
    }

    private List<ProductResponseDTO> convertToProductDTOs(List<Integer> productIds) {
        return productIds.stream()
                .map(id -> {
                    try {
                        return productService.getProductById(id);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<Float> toList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float f : arr) list.add(f);
        return list;
    }
}
