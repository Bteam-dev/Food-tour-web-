package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.DTO.ProductDTO.ProductResponseDTO;
import com.example.FoodTourApp.entity.Product;
import com.example.FoodTourApp.entity.UserBehavior;
import com.example.FoodTourApp.entity.UserBehavior.ActionType;
import com.example.FoodTourApp.entity.UserBehavior.BehaviorSource;
import com.example.FoodTourApp.repository.OrderItemRepository;
import com.example.FoodTourApp.repository.ProductRepository;
import com.example.FoodTourApp.repository.UserBehaviorRepository;
import com.example.FoodTourApp.repository.UserRepository;
import com.example.FoodTourApp.service.ProductService;
import com.example.FoodTourApp.service.RealTimeRecommendationService;
import com.example.FoodTourApp.service.UserBehaviorBufferService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Collections;
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
    private final UserBehaviorBufferService behaviorBufferService;
    private final OrderItemRepository orderItemRepository;
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
    private static final String MODEL_BATCH_RECS_KEY = "rec:model:batch:";
    private static final Duration EMBEDDING_TTL = Duration.ofHours(6);
    private static final Duration BATCH_RECS_TTL = Duration.ofDays(7); // hết hạn sau 1 tuần hoặc khi retrain
    
    // Preloaded data — productEmbeddings là nguồn dữ liệu chính, load từ ES
    private final Map<Integer, float[]> productEmbeddings = new java.util.concurrent.ConcurrentHashMap<>();
    // Fallback embedding cho product chưa có trong ES (cold-start sản phẩm mới): trung bình theo category
    private final Map<Integer, float[]> categoryAverageEmbeddings = new java.util.concurrent.ConcurrentHashMap<>();
    private Map<Integer, Integer> productShopMap = new HashMap<>();
    private Map<Integer, Integer> productCategoryMap = new HashMap<>();
    private volatile int embeddingDim = 64;

    @PostConstruct
    public void init() {
        // Metadata phải load trước để buildCategoryAverageEmbeddings dùng được productCategoryMap
        loadProductMetadata();
        // ES là primary source — load tất cả embeddings vào memory
        loadAllProductEmbeddingsFromEs();
        buildCategoryAverageEmbeddings();
        loadPrecomputedRecsToRedis();
    }

    /**
     * Load TẤT CẢ product embeddings từ ES products_recommend vào in-memory map.
     *
     * Tại sao ES là primary, JSON là fallback?
     * - ES là serving store — luôn là snapshot mới nhất sau mỗi lần retrain + sync
     * - JSON file là artifact tạm thời từ Colab, có thể out-of-sync
     * - Nếu ES không available lúc startup → fallback sang JSON để app vẫn chạy được
     */
    @SuppressWarnings("unchecked")
    private void loadAllProductEmbeddingsFromEs() {
        try {
            RestTemplate rt = new RestTemplate();
            // Food app scale (< 10k products): single request với size lớn là đủ, không cần scroll
            var resp = rt.postForEntity(
                    ES_URL + "/" + ES_INDEX + "/_search",
                    Map.of(
                            "query", Map.of("match_all", Map.of()),
                            "size", 10000,
                            "_source", List.of("product_id", "embedding")
                    ),
                    Map.class
            );
            if (resp.getBody() == null) {
                log.warn("⚠️ ES returned null when loading embeddings — falling back to JSON");
                loadProductEmbeddingsFromJson();
                return;
            }

            Map<String, Object> hitsWrapper = (Map<String, Object>) resp.getBody().get("hits");
            List<Map<String, Object>> hits = (List<Map<String, Object>>) hitsWrapper.get("hits");
            if (hits == null || hits.isEmpty()) {
                log.warn("⚠️ ES index '{}' has no documents — falling back to JSON", ES_INDEX);
                loadProductEmbeddingsFromJson();
                return;
            }

            Map<Integer, float[]> temp = new HashMap<>(hits.size());
            for (Map<String, Object> hit : hits) {
                Map<String, Object> source = (Map<String, Object>) hit.get("_source");
                if (source == null) continue;
                Object pidObj = source.get("product_id");
                Object embObj = source.get("embedding");
                if (pidObj == null || !(embObj instanceof List)) continue;

                List<?> embList = (List<?>) embObj;
                float[] emb = new float[embList.size()];
                for (int i = 0; i < embList.size(); i++) {
                    emb[i] = ((Number) embList.get(i)).floatValue();
                }
                temp.put(((Number) pidObj).intValue(), emb);
            }

            productEmbeddings.clear();
            productEmbeddings.putAll(temp);
            if (!productEmbeddings.isEmpty()) {
                embeddingDim = productEmbeddings.values().iterator().next().length;
            }
            log.info("✅ Loaded {} product embeddings from ES (dim={})", productEmbeddings.size(), embeddingDim);

        } catch (Exception e) {
            log.warn("⚠️ ES unavailable during embedding load ({}), falling back to JSON", e.getMessage());
            loadProductEmbeddingsFromJson();
        }
    }

    /** Fallback: load từ JSON khi ES không available */
    private void loadProductEmbeddingsFromJson() {
        try {
            var resource = new ClassPathResource(
                    "models/FoodRecommendSearchByBehavior/model_output/product_embeddings.json");
            if (!resource.exists()) {
                log.warn("⚠️ product_embeddings.json not found either — no embeddings loaded");
                return;
            }
            Map<String, List<Double>> raw = objectMapper.readValue(
                    resource.getInputStream(), new TypeReference<>() {});
            raw.forEach((k, v) -> {
                float[] arr = new float[v.size()];
                for (int i = 0; i < v.size(); i++) arr[i] = v.get(i).floatValue();
                productEmbeddings.put(Integer.parseInt(k), arr);
            });
            if (!productEmbeddings.isEmpty()) {
                embeddingDim = productEmbeddings.values().iterator().next().length;
            }
            log.info("✅ Loaded {} product embeddings from JSON fallback (dim={})",
                    productEmbeddings.size(), embeddingDim);
        } catch (Exception e) {
            log.error("⚠️ Failed to load product embeddings from JSON: {}", e.getMessage());
        }
    }

    /**
     * Tính embedding trung bình cho mỗi category từ tất cả products đã có embedding.
     *
     * Dùng làm fallback cho products mới thêm sau lần retrain cuối — thay vì bỏ qua hoàn toàn,
     * dùng "trung bình category" để ước lượng vị trí của sản phẩm trong embedding space.
     * Khi model retrain lần tới, embedding thật sẽ thay thế proxy này.
     */
    private void buildCategoryAverageEmbeddings() {
        if (productEmbeddings.isEmpty() || productCategoryMap.isEmpty()) return;

        Map<Integer, List<float[]>> byCategory = new HashMap<>();
        for (Map.Entry<Integer, float[]> e : productEmbeddings.entrySet()) {
            Integer catId = productCategoryMap.get(e.getKey());
            if (catId != null) {
                byCategory.computeIfAbsent(catId, k -> new ArrayList<>()).add(e.getValue());
            }
        }

        categoryAverageEmbeddings.clear();
        for (Map.Entry<Integer, List<float[]>> e : byCategory.entrySet()) {
            float[] avg = averageAndNormalize(e.getValue());
            if (avg != null) categoryAverageEmbeddings.put(e.getKey(), avg);
        }
        log.info("✅ Built category average embeddings for {} categories", categoryAverageEmbeddings.size());
    }

    private float[] averageAndNormalize(List<float[]> embeddings) {
        if (embeddings.isEmpty()) return null;
        int dim = embeddings.get(0).length;
        float[] avg = new float[dim];
        for (float[] emb : embeddings) {
            for (int i = 0; i < dim; i++) avg[i] += emb[i];
        }
        for (int i = 0; i < dim; i++) avg[i] /= embeddings.size();
        float norm = 0;
        for (float v : avg) norm += v * v;
        norm = (float) Math.sqrt(norm);
        if (norm > 0) for (int i = 0; i < dim; i++) avg[i] /= norm;
        return avg;
    }

    /**
     * Lấy embedding của product theo thứ tự ưu tiên:
     * 1. Embedding chính xác từ Two-Tower model (đã load từ ES)
     * 2. Category average (proxy cho sản phẩm mới sau retrain gần nhất)
     * 3. null (không có thông tin gì)
     */
    private float[] getProductEmbedding(Integer productId) {
        float[] emb = productEmbeddings.get(productId);
        if (emb != null) return emb;
        // Proxy: dùng trung bình category — tốt hơn là bỏ qua hoàn toàn sản phẩm mới
        Integer catId = productCategoryMap.get(productId);
        return catId != null ? categoryAverageEmbeddings.get(catId) : null;
    }

    /**
     * Refresh embeddings từ ES mỗi 2 tiếng — tự động pick up embeddings của sản phẩm mới
     * được thêm vào ES sau mỗi lần retrain mà không cần restart app.
     */
    @Scheduled(cron = "0 0 */2 * * *")
    public void scheduledRefreshEmbeddings() {
        log.info("🔄 Scheduled refresh: reloading product embeddings from ES...");
        loadAllProductEmbeddingsFromEs();
        buildCategoryAverageEmbeddings();
        log.info("✅ Scheduled refresh done: {} embeddings (dim={})", productEmbeddings.size(), embeddingDim);
    }

    /**
     * Load precomputed_recs.json (kết quả thật của Two-Tower model) vào Redis.
     * Format: {"userId": [productId1, productId2, ...top50...], ...}
     * Được gọi lúc startup và sau mỗi lần retrain.
     */
    private void loadPrecomputedRecsToRedis() {
        try {
            var resource = new ClassPathResource(
                    "models/FoodRecommendSearchByBehavior/model_output/precomputed_recs.json");
            if (!resource.exists()) {
                log.warn("⚠️ precomputed_recs.json not found — batch layer disabled");
                return;
            }
            Map<String, List<Integer>> raw = objectMapper.readValue(
                    resource.getInputStream(), new TypeReference<>() {});
            for (Map.Entry<String, List<Integer>> entry : raw.entrySet()) {
                String key = MODEL_BATCH_RECS_KEY + entry.getKey();
                redisObjectTemplate.opsForValue().set(key, entry.getValue(), BATCH_RECS_TTL);
            }
            log.info("✅ Loaded precomputed recs for {} users into Redis (TTL=7d)", raw.size());
        } catch (Exception e) {
            log.warn("⚠️ Failed to load precomputed_recs.json: {}", e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PRODUCT SYNC SUPPORT
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public float[] getCategoryEmbedding(Integer categoryId) {
        if (categoryId == null) return null;
        return categoryAverageEmbeddings.get(categoryId);
    }

    @Override
    public synchronized void registerProduct(Integer productId, Integer categoryId, Integer shopId, float[] embedding) {
        if (embedding != null) productEmbeddings.put(productId, embedding);
        if (categoryId != null) productCategoryMap.put(productId, categoryId);
        if (shopId != null) productShopMap.put(productId, shopId);
        log.debug("Registered product {} in-memory (categoryId={}, shopId={})", productId, categoryId, shopId);
    }

    @Override
    public synchronized void removeProduct(Integer productId) {
        productEmbeddings.remove(productId);
        productCategoryMap.remove(productId);
        productShopMap.remove(productId);
        log.debug("Removed product {} from in-memory maps", productId);
    }

    /**
     * Hot-reload product embeddings + precomputed recs sau khi retrain Two-Tower.
     * Thread-safe: clear + reload atomically.
     */
    @Override
    public synchronized int reloadProductEmbeddings() {
        log.info("🔄 Hot-reloading: ES embeddings + category averages + precomputed recs...");
        loadAllProductEmbeddingsFromEs();
        buildCategoryAverageEmbeddings();
        loadPrecomputedRecsToRedis();
        log.info("✅ Hot-reload done: {} embeddings (dim={})", productEmbeddings.size(), embeddingDim);
        return productEmbeddings.size();
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
    // @Transactional bỏ: không còn write trong method này
    // enable_lazy_load_no_trans=true cho phép lazy-load ngoài transaction
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

            double weight     = calculateWeight(actionType, viewDurationSeconds, quantity, rating);
            Integer categoryId = product.getCategory().getId(); // lazy load ok do enable_lazy_load_no_trans
            Integer shopId     = product.getShop().getId();

            // Buffer vào Redis thay vì ghi thẳng MySQL
            // → Batch flush vào MySQL mỗi 30s bởi UserBehaviorBufferServiceImpl
            behaviorBufferService.buffer(userId, productId, actionType, weight,
                    sessionId, source, viewDurationSeconds, quantity, rating,
                    categoryId, shopId);

            log.debug("Buffered {} for user {} on product {}", actionType, userId, productId);

            // Async update user embedding (từ MySQL data hiện tại, max lag 30s)
            // Lag 30s chấp nhận được vì embedding cache TTL là 6 giờ
            updateUserEmbeddingAsync(userId);

        } catch (Exception e) {
            log.error("Failed to track behavior: {}", e.getMessage());
        }
    }

    private double calculateWeight(ActionType actionType, Integer duration, Integer quantity, Integer rating) {
        double baseWeight = actionType.getDefaultWeight();
        
        // Adjust weight based on context
        if (actionType == ActionType.VIEW && duration != null) {
            if (duration > 60) baseWeight *= 2.0;       // > 60s: xem kỹ
            else if (duration > 30) baseWeight *= 1.5;  // > 30s: quan tâm
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
                existingView.setViewDurationSeconds(durationSeconds);
                behaviorRepository.save(existingView);
                log.info("Updated view duration: userId={}, productId={}, sessionId={}, duration={}s",
                    userId, productId, sessionId, durationSeconds);
                if (userId != null) {
                    updateUserEmbedding(userId);
                }
            } else {
                log.warn("No VIEW record to update duration: userId={}, productId={}, sessionId={}",
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
     * Tính user embedding = weighted average của product embeddings đã tương tác.
     *
     * Formula: user_emb = Σ(decay_i * weight_i * product_emb_i) / Σ(decay_i * weight_i)
     *
     * Time decay: decay = 0.7^(days_ago)
     *   - Hôm nay:   1.0
     *   - 1 ngày:    0.70
     *   - 3 ngày:    0.34
     *   - 7 ngày:    0.08
     *   - 14 ngày:   0.007
     * → Behaviors mới chỉ cần vài lần click là áp đảo hành vi cũ 1-2 tuần trước.
     */
    private float[] computeUserEmbedding(Integer userId) {
        LocalDateTime since = LocalDateTime.now().minusDays(14);
        List<Object[]> interactions = behaviorRepository.findUserProductWeights(userId, since);

        if (interactions.isEmpty()) {
            return null;
        }

        float[] embedding = new float[embeddingDim];
        double totalWeight = 0;
        LocalDateTime now = LocalDateTime.now();

        for (Object[] row : interactions) {
            Integer productId = (Integer) row[0];
            Double weight = ((Number) row[1]).doubleValue();
            LocalDateTime lastInteraction = (LocalDateTime) row[2];

            if (weight <= 0) continue;

            float[] productEmb = getProductEmbedding(productId);
            if (productEmb == null) continue;

            // Time decay: 0.7^(days_ago) — behaviors cũ tụt nhanh, behaviors mới chiếm ưu thế
            double hoursAgo = java.time.Duration.between(lastInteraction, now).toMinutes() / 60.0;
            double daysAgo = hoursAgo / 24.0;
            double decay = Math.pow(0.7, daysAgo);
            double effectiveWeight = weight * decay;

            for (int i = 0; i < embeddingDim; i++) {
                embedding[i] += productEmb[i] * (float) effectiveWeight;
            }
            totalWeight += effectiveWeight;
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

        // Exclude sản phẩm user đã phản hồi tiêu cực
        excludeIds.addAll(behaviorRepository.findNegativeProductIds(userId));

        // ── CANDIDATE GENERATION ───────────────────────────────────────────────
        // Tỷ lệ pool: realtime 80% + batch 20%
        //
        // Lý do:
        // - Realtime (ES KNN từ user embedding) = phản ánh sở thích HIỆN TẠI của user
        // - Batch (precomputed Two-Tower) = sở thích lịch sử, dùng để diversity/exploration
        // - Nếu batch quá nhiều (50/50 cũ) → batch "chèn" candidates cũ vào pool,
        //   rerank không đủ mạnh để đẩy chúng xuống → phở cũ cứ nổi lên

        // Nguồn 1 (PRIMARY): Real-time candidates từ ES KNN — dùng user embedding được update
        // ngay sau mỗi interaction (chè click → user emb dịch về vùng chè → ES KNN trả về chè)
        float[] userEmb = getUserEmbedding(userId);
        List<Integer> realtimeCandidates = (userEmb != null)
                ? searchByEmbedding(userEmb, excludeIds, limit * 3)  // pool lớn để rerank có đủ candidates
                : Collections.emptyList();

        // Nguồn 2 (DIVERSITY): Batch candidates từ Two-Tower model — chỉ lấy limit/2
        // để đảm bảo có độ đa dạng, không để realttime quá "hẹp"
        List<Integer> batchCandidates = getBatchCandidates(userId);
        int batchQuota = Math.min(batchCandidates.size(), limit / 2);

        // Trộn: realtime TRƯỚC (ưu tiên sở thích hiện tại), batch chỉ bổ sung diversity ở cuối
        Set<Integer> seen = new LinkedHashSet<>();
        realtimeCandidates.stream().filter(id -> !excludeIds.contains(id)).forEach(seen::add);
        batchCandidates.stream()
                .filter(id -> !excludeIds.contains(id) && !seen.contains(id))
                .limit(batchQuota)
                .forEach(seen::add);
        List<Integer> blendedPool = new ArrayList<>(seen);

        log.debug("[Rec] Pool: realtime={}, batch(capped={})={}, blended={} for user {}",
                realtimeCandidates.size(), batchQuota, batchCandidates.size(), blendedPool.size(), userId);

        // Rerank toàn bộ pool bằng real-time behavioral signal
        if (!blendedPool.isEmpty()) {
            resultIds.addAll(rerank(userId, blendedPool, excludeIds));
        }

        // ── FALLBACK LAYER ─────────────────────────────────────────────────────
        // Session-based: sản phẩm tương tự những gì user xem trong session này
        if (sessionId != null && resultIds.size() < limit) {
            for (Integer pid : behaviorRepository.findProductIdsBySessionId(sessionId)) {
                if (excludeIds.contains(pid)) continue;
                for (Integer simId : getSimilarProductIds(pid, 5)) {
                    if (!excludeIds.contains(simId) && !resultIds.contains(simId))
                        resultIds.add(simId);
                }
            }
        }

        // Category-based
        if (resultIds.size() < limit) {
            for (Object[] row : behaviorRepository.findUserCategoryPreferences(userId, PageRequest.of(0, 3))) {
                Integer categoryId = (Integer) row[0];
                for (Product p : productRepository.findByCategoryIdAndIsAvailableTrue(categoryId, PageRequest.of(0, 20))) {
                    if (!excludeIds.contains(p.getId()) && !resultIds.contains(p.getId())) {
                        resultIds.add(p.getId());
                        if (resultIds.size() >= limit * 2) break;
                    }
                }
            }
        }

        // Shop-based
        if (resultIds.size() < limit) {
            for (Object[] row : behaviorRepository.findUserShopPreferences(userId, PageRequest.of(0, 3))) {
                Integer shopId = (Integer) row[0];
                for (Product p : productRepository.findByShopIdAndIsAvailableTrue(shopId, PageRequest.of(0, 20))) {
                    if (!excludeIds.contains(p.getId()) && !resultIds.contains(p.getId())) {
                        resultIds.add(p.getId());
                        if (resultIds.size() >= limit * 2) break;
                    }
                }
            }
        }

        // Cold-start: trending + diverse top-rated
        if (resultIds.size() < limit) {
            for (Integer id : getColdStartProductIds(excludeIds, limit * 2)) {
                if (!resultIds.contains(id)) resultIds.add(id);
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
            // Cold start: trending + diverse top-rated (không có session history)
            List<Integer> coldStartIds = getColdStartProductIds(Collections.emptySet(), limit);
            return convertToProductDTOs(coldStartIds.subList(0, Math.min(limit, coldStartIds.size())));
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

    /**
     * Sản phẩm tương tự — Hybrid: Content KNN + CF co-interaction rerank.
     *
     * Kiến trúc:
     * 1. Content pool: KNN search bằng product embedding (semantic similarity)
     * 2. CF pool: sản phẩm hay được tương tác cùng nhau (co-view, co-cart, co-purchase)
     * 3. Merge pool, score từng candidate:
     *    final_score = 0.6 * content_score + 0.4 * cf_score
     *
     * Kết quả: khác hoàn toàn với alsoBought (CF-dominant) và becauseYouViewed (user-personalized).
     */
    @Override
    public List<ProductResponseDTO> getSimilarProducts(Integer productId, int limit) {
        float[] productEmb = getProductEmbedding(productId);

        // ── CONTENT POOL (KNN) ─────────────────────────────────────────────────
        List<Integer> contentPool = (productEmb != null)
                ? searchByEmbedding(productEmb, Set.of(productId), limit * 3)
                : productRepository.findSimilarByCategory(productId, limit * 3).stream()
                        .map(Product::getId).collect(Collectors.toList());

        // ── CF POOL (co-interaction) ───────────────────────────────────────────
        // Lấy products mà users hay tương tác cùng với productId
        List<Object[]> cfRows = behaviorRepository.findCoInteractedProducts(
                productId, PageRequest.of(0, limit * 3));
        Map<Integer, Double> cfScoreMap = new HashMap<>();
        double cfMax = 1.0;
        for (Object[] row : cfRows) {
            Integer pid = (Integer) row[0];
            double score = ((Number) row[1]).doubleValue();
            cfScoreMap.put(pid, score);
            if (score > cfMax) cfMax = score;
        }
        final double cfNorm = cfMax;

        // ── MERGE & SCORE ──────────────────────────────────────────────────────
        Set<Integer> merged = new LinkedHashSet<>(contentPool);
        cfScoreMap.keySet().stream()
                .filter(id -> !id.equals(productId))
                .forEach(merged::add);

        // Precompute content scores: rank-based (KNN order → rank score 1/rank)
        Map<Integer, Double> contentScoreMap = new HashMap<>();
        int rank = 1;
        for (Integer id : contentPool) {
            contentScoreMap.put(id, 1.0 / rank++);
        }
        double contentMax = contentPool.isEmpty() ? 1.0 : 1.0; // already 1/1 = 1.0

        List<Integer> sorted = merged.stream()
                .filter(id -> !id.equals(productId))
                .sorted(Comparator.comparingDouble((Integer id) -> {
                    double cs = contentScoreMap.getOrDefault(id, 0.0); // already [0,1]
                    double cf = cfScoreMap.getOrDefault(id, 0.0) / cfNorm;
                    return -(0.6 * cs + 0.4 * cf);
                }))
                .limit(limit)
                .collect(Collectors.toList());

        return convertToProductDTOs(sorted);
    }

    /**
     * Vì bạn đã xem — Personalized Hybrid: blended query vector (CB + user signal).
     *
     * Kiến trúc:
     * 1. query_vec = 0.6 * product_emb + 0.4 * user_emb (L2-normalized)
     *    - product_emb: content signal — gợi ý thứ GIỐNG sản phẩm đó
     *    - user_emb: personalization signal — gợi ý thứ PHÙ HỢP sở thích user
     * 2. KNN search trên ES bằng query_vec đã blend
     * 3. Exclude products user đã tương tác
     *
     * Kết quả: khác similarProducts (không có user signal) và alsoBought (không dùng embedding).
     */
    @Override
    public List<ProductResponseDTO> getBecauseYouViewed(Integer userId, Integer viewedProductId, int limit) {
        Set<Integer> excludeIds = new HashSet<>();
        excludeIds.add(viewedProductId);
        behaviorRepository.findRecentByUserId(userId, PageRequest.of(0, 50))
                .forEach(b -> excludeIds.add(b.getProduct().getId()));

        float[] productEmb = getProductEmbedding(viewedProductId);
        float[] userEmb = getUserEmbedding(userId);

        float[] queryVec;
        if (productEmb != null && userEmb != null && productEmb.length == userEmb.length) {
            // Hybrid query: product content (60%) + user preference (40%)
            queryVec = blendAndNormalize(productEmb, 0.6f, userEmb, 0.4f);
        } else if (productEmb != null) {
            queryVec = productEmb;
        } else if (userEmb != null) {
            queryVec = userEmb;
        } else {
            // Cold-start: trending in same category
            return getColdStartForProduct(viewedProductId, excludeIds, limit);
        }

        List<Integer> candidates = searchByEmbedding(queryVec, excludeIds, limit * 2);
        return convertToProductDTOs(candidates.subList(0, Math.min(limit, candidates.size())));
    }

    /**
     * Người mua cũng mua — CF-dominant Hybrid: co-purchase candidates reranked by content.
     *
     * Kiến trúc:
     * 1. CF candidates (PRIMARY):
     *    a. Co-purchase từ actual orders (OrderItem): signal mạnh nhất
     *    b. Co-purchase từ UserBehavior PURCHASE: supplement
     *    c. Weak CF: co-cart / co-wishlist khi purchase data thưa
     * 2. Content rerank: geometric mean của CF score và cosine similarity
     *    final_score = sqrt(cf_score * content_score)
     * 3. Fallback: trending trong cùng category (KHÔNG dùng KNN — khác hoàn toàn)
     *
     * Kết quả: khác similarProducts (content-dominant) và becauseYouViewed (user-personalized).
     */
    @Override
    public List<ProductResponseDTO> getAlsoBought(Integer productId, int limit) {
        float[] productEmb = getProductEmbedding(productId);

        // ── CF: Order-level co-purchase (strongest signal) ────────────────────
        List<Object[]> orderRows = orderItemRepository.findCoPurchasedInOrders(
                productId, PageRequest.of(0, limit * 4));
        Map<Integer, Double> cfScoreMap = new LinkedHashMap<>();
        for (Object[] row : orderRows) {
            cfScoreMap.put((Integer) row[0], ((Number) row[1]).doubleValue());
        }

        // ── CF: Behavior-level PURCHASE co-occurrence (supplement) ────────────
        if (cfScoreMap.size() < limit) {
            List<Object[]> behaviorRows = behaviorRepository.findCoPurchasedProducts(
                    productId, PageRequest.of(0, limit * 4));
            for (Object[] row : behaviorRows) {
                cfScoreMap.merge((Integer) row[0], ((Number) row[1]).doubleValue(), Double::sum);
            }
        }

        // ── CF: Weak signals (co-cart/co-wishlist) khi purchase data thưa ──────
        if (cfScoreMap.size() < limit / 2) {
            List<Object[]> weakRows = behaviorRepository.findCoInteractedProducts(
                    productId, PageRequest.of(0, limit * 4));
            for (Object[] row : weakRows) {
                // Discount weak signals: weight × 0.3
                cfScoreMap.merge((Integer) row[0], ((Number) row[1]).doubleValue() * 0.3, Double::sum);
            }
        }

        // ── HYBRID RERANK: CF score × content similarity ──────────────────────
        if (!cfScoreMap.isEmpty()) {
            double cfMax = cfScoreMap.values().stream().mapToDouble(v -> v).max().orElse(1.0);
            final double cfNorm = cfMax > 0 ? cfMax : 1.0;

            List<Integer> sorted = cfScoreMap.entrySet().stream()
                    .filter(e -> !e.getKey().equals(productId))
                    .sorted(Comparator.comparingDouble(e -> {
                        double cfScore = e.getValue() / cfNorm;
                        // Blend với content similarity (geometric mean)
                        if (productEmb != null) {
                            float[] candEmb = getProductEmbedding(e.getKey());
                            double contentScore = (candEmb != null)
                                    ? (cosineSimilarity(productEmb, candEmb) + 1) / 2  // normalize [-1,1] → [0,1]
                                    : 0.5;
                            // Geometric mean: sqrt(cf * content) — cân bằng 2 signal
                            return -Math.sqrt(cfScore * contentScore);
                        }
                        return -cfScore;
                    }))
                    .map(Map.Entry::getKey)
                    .limit(limit)
                    .collect(Collectors.toList());

            if (!sorted.isEmpty()) {
                return convertToProductDTOs(sorted);
            }
        }

        // ── FALLBACK: Trending trong cùng category (khác hoàn toàn với KNN) ────
        return getColdStartForProduct(productId, Set.of(productId), limit);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LAMBDA ARCHITECTURE — BATCH + SPEED LAYER
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Lấy batch candidates từ Redis (kết quả thật của Two-Tower model).
     * Key: rec:model:batch:{userId} → List<Integer> productIds (ordered by model score)
     */
    @SuppressWarnings("unchecked")
    private List<Integer> getBatchCandidates(Integer userId) {
        Object cached = redisObjectTemplate.opsForValue().get(MODEL_BATCH_RECS_KEY + userId);
        if (cached instanceof List<?> list) {
            return list.stream().map(o -> ((Number) o).intValue()).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    /**
     * Rerank pool candidates bằng real-time behavioral signal.
     *
     * Công thức: final_score = α * position_score + β * realtime_boost
     *   - position_score = 1/(rank+1)  → vị trí trong blended pool (realtime trước, batch sau)
     *   - realtime_boost = Σ cosine_sim(candidate, interacted) * decay * weight → 72h gần nhất
     *   - α = 0.15 (position), β = 0.85 (realtime) → realtime signal áp đảo hoàn toàn
     *
     * Với α=0.15/β=0.85: user vừa click chè/cơm trộn → boost cosine rất mạnh → chúng nổi lên đầu
     * ngay cả khi batch đặt chúng thấp hơn.
     */
    private List<Integer> rerank(Integer userId, List<Integer> candidates, Set<Integer> excludeIds) {
        // 72h: bắt interaction trong 3 ngày gần nhất — đủ "real-time" mà không quá hẹp
        List<Object[]> recentInteractions = behaviorRepository.findUserProductWeights(
                userId, LocalDateTime.now().minusHours(72));

        Map<Integer, Double> boostMap = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();

        for (Object[] row : recentInteractions) {
            Integer interactedId = (Integer) row[0];
            double weight = ((Number) row[1]).doubleValue();
            LocalDateTime lastInteraction = (LocalDateTime) row[2];
            // Ưu tiên local JSON, fallback sang ES nếu product chưa có trong training set
            float[] interactedEmb = getProductEmbedding(interactedId);
            if (interactedEmb == null || weight <= 0) continue;

            // Time decay: 0.7^(daysAgo) — behaviors cũ hơn 1 ngày tụt nhanh
            double daysAgo = java.time.Duration.between(lastInteraction, now).toMinutes() / (60.0 * 24);
            double effectiveWeight = weight * Math.pow(0.7, daysAgo);

            // Tính cosine similarity với từng candidate
            for (Integer candidateId : candidates) {
                if (excludeIds.contains(candidateId)) continue;
                float[] candidateEmb = getProductEmbedding(candidateId);
                if (candidateEmb == null) continue;
                boostMap.merge(candidateId,
                        cosineSimilarity(interactedEmb, candidateEmb) * effectiveWeight,
                        Double::sum);
            }
        }

        // Pre-compute rank map để tránh O(n²) trong sort
        Map<Integer, Integer> rankMap = new HashMap<>();
        for (int i = 0; i < candidates.size(); i++) rankMap.put(candidates.get(i), i);

        double maxBoost = boostMap.values().stream().mapToDouble(v -> v).max().orElse(1.0);
        if (maxBoost == 0) maxBoost = 1.0;
        final double normalizer = maxBoost;

        return candidates.stream()
                .filter(id -> !excludeIds.contains(id))
                .sorted(Comparator.comparingDouble((Integer id) -> {
                    double posScore = 1.0 / (rankMap.getOrDefault(id, candidates.size()) + 1);
                    double boost    = boostMap.getOrDefault(id, 0.0) / normalizer;
                    // α=0.15 position (pool order), β=0.85 realtime signal
                    // → sở thích hiện tại (chè, cơm trộn) sẽ nổi lên ngay lập tức
                    return -(0.15 * posScore + 0.85 * boost);
                }))
                .collect(Collectors.toList());
    }

    /**
     * Blend 2 embeddings theo weight rồi L2-normalize.
     * Dùng cho getBecauseYouViewed: query_vec = 0.6*product + 0.4*user
     */
    private float[] blendAndNormalize(float[] a, float wA, float[] b, float wB) {
        float[] result = new float[a.length];
        for (int i = 0; i < a.length; i++) {
            result[i] = a[i] * wA + b[i] * wB;
        }
        float norm = 0;
        for (float v : result) norm += v * v;
        norm = (float) Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < result.length; i++) result[i] /= norm;
        }
        return result;
    }

    /**
     * Fallback cho cold-start: trending trong cùng category của productId.
     * Dùng cho getAlsoBought và getBecauseYouViewed khi không có đủ data.
     * KHÔNG dùng KNN để đảm bảo kết quả khác với getSimilarProducts.
     */
    private List<ProductResponseDTO> getColdStartForProduct(Integer productId, Set<Integer> excludeIds, int limit) {
        Integer catId = productCategoryMap.get(productId);

        // Trending trong cùng category
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        List<Object[]> trending = behaviorRepository.findTrendingProductIds(sevenDaysAgo, PageRequest.of(0, limit * 3));
        List<Integer> result = trending.stream()
                .map(row -> (Integer) row[0])
                .filter(id -> !excludeIds.contains(id))
                .filter(id -> catId == null || catId.equals(productCategoryMap.get(id)))
                .limit(limit)
                .collect(Collectors.toList());

        // Fill với top-rated trong category nếu trending không đủ
        if (result.size() < limit && catId != null) {
            List<Product> catProducts = productRepository.findByCategoryIdAndIsAvailableTrue(
                    catId, PageRequest.of(0, limit * 2));
            for (Product p : catProducts) {
                if (!excludeIds.contains(p.getId()) && !result.contains(p.getId())) {
                    result.add(p.getId());
                    if (result.size() >= limit) break;
                }
            }
        }

        return convertToProductDTOs(result);
    }

    private double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot  += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom > 0 ? dot / denom : 0.0;
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
        float[] embedding = getProductEmbedding(productId);
        if (embedding != null) {
            return searchByEmbedding(embedding, Set.of(productId), limit);
        }
        // Fallback: same category (product hoàn toàn không có embedding và không có category)
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

    // ══════════════════════════════════════════════════════════════════════════
    // COLD-START STRATEGY
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Cold-start recommendations cho user mới hoặc session rỗng.
     *
     * Chiến lược 3 tầng:
     * 1. Trending (7 ngày): sản phẩm được mua nhiều nhất gần đây — phản ánh xu hướng thực
     * 2. Diverse top-rated: 1 sản phẩm tốt nhất mỗi danh mục — đảm bảo đa dạng
     * 3. Pure top-rated: fill nếu 2 tầng trên chưa đủ
     *
     * Kết quả apply diversity rule (max 2/shop) trước khi trả về.
     */
    private List<Integer> getColdStartProductIds(Set<Integer> excludeIds, int limit) {
        List<Integer> result = new ArrayList<>();

        // Tầng 1: Trending — được mua nhiều nhất trong 7 ngày qua
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        List<Object[]> trendingRows = behaviorRepository.findTrendingProductIds(
                sevenDaysAgo, PageRequest.of(0, limit));

        List<Integer> trendingProductIds = trendingRows.stream()
                .map(row -> (Integer) row[0])
                .collect(Collectors.toList());

        // Lọc những product còn available
        if (!trendingProductIds.isEmpty()) {
            List<Product> trendingProducts = productRepository.findAvailableByIds(trendingProductIds);
            // Giữ thứ tự theo trending score (sort theo vị trí trong trendingProductIds)
            Map<Integer, Integer> rankMap = new HashMap<>();
            for (int i = 0; i < trendingProductIds.size(); i++) {
                rankMap.put(trendingProductIds.get(i), i);
            }
            trendingProducts.sort(Comparator.comparingInt(p -> rankMap.getOrDefault(p.getId(), 999)));

            for (Product p : trendingProducts) {
                if (!excludeIds.contains(p.getId()) && !result.contains(p.getId())) {
                    result.add(p.getId());
                }
            }
            log.debug("[ColdStart] Trending added {} products", result.size());
        }

        // Tầng 2: Diverse top-rated — 1 sản phẩm tốt nhất mỗi danh mục
        if (result.size() < limit) {
            List<Product> allByCategory = productRepository.findAvailableOrderedByCategoryAndScore(
                    PageRequest.of(0, limit * 5));  // pool lớn để đủ category

            Set<Integer> seenCategories = new HashSet<>();
            for (Product p : allByCategory) {
                if (result.size() >= limit) break;
                Integer catId = p.getCategory() != null ? p.getCategory().getId() : null;
                if (catId != null && !seenCategories.contains(catId)
                        && !excludeIds.contains(p.getId()) && !result.contains(p.getId())) {
                    seenCategories.add(catId);
                    result.add(p.getId());
                }
            }
            log.debug("[ColdStart] After diverse top-rated: {} products", result.size());
        }

        // Tầng 3: Pure top-rated — fill nếu vẫn thiếu
        if (result.size() < limit) {
            List<Product> topRated = productRepository.findTopRatedAvailable(limit * 2);
            for (Product p : topRated) {
                if (!excludeIds.contains(p.getId()) && !result.contains(p.getId())) {
                    result.add(p.getId());
                }
                if (result.size() >= limit) break;
            }
        }

        return applyDiversity(result, limit);
    }
}
