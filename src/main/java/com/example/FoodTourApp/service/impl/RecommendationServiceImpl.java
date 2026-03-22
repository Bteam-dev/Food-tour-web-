package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.DTO.RecommendDTO.ProductRecommendDTO;
import com.example.FoodTourApp.entity.Product;
import com.example.FoodTourApp.repository.OrderItemRepository;
import com.example.FoodTourApp.repository.ProductRepository;
import com.example.FoodTourApp.repository.WishlistRepository;
import com.example.FoodTourApp.service.RecommendationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationServiceImpl implements RecommendationService {

    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final WishlistRepository wishlistRepository;
    private final ObjectMapper objectMapper;

    private Map<Integer, List<Integer>> precomputedRecs = new HashMap<>();
    private Map<Integer, Integer> productShopCache = new HashMap<>();

    private static final String ES_URL = "http://localhost:9200";
    private static final String ES_INDEX = "products_recommend";

    @PostConstruct
    public void loadPrecomputedRecs() {
        try {
            var resource = new ClassPathResource("models/content/model_output/precomputed_recs.json");            Map<String, List<Integer>> raw = objectMapper.readValue(
                    resource.getInputStream(),
                    new TypeReference<>() {}
            );
            raw.forEach((k, v) -> precomputedRecs.put(Integer.parseInt(k), v));
            log.info("✅ Loaded precomputed recs for {} users", precomputedRecs.size());
        } catch (Exception e) {
            log.warn("⚠️ Không load được precomputed_recs.json: {}", e.getMessage());
        }
    }

    @Override
    public List<ProductRecommendDTO> getForYou(Integer userId, int limit) {

        Set<Integer> seenProducts = getSeenProductIds(userId);

        List<Integer> preIds = new ArrayList<>();
        if (precomputedRecs.containsKey(userId)) {
            preIds = precomputedRecs.get(userId).stream()
                    .filter(pid -> !seenProducts.contains(pid))
                    .collect(Collectors.toList());
        }

        long timeSeed = System.currentTimeMillis() / (1000L * 60 * 30);
        Collections.shuffle(preIds, new Random(userId * 31L + timeSeed));

        int preCount = (int) Math.ceil(limit * 0.7);
        int esCount = limit - preCount;

        List<Integer> preSlice = preIds.stream()
                .limit(preCount)
                .collect(Collectors.toList());

        List<Integer> esIds = getRealtimeFromES(userId, seenProducts, preSlice, esCount);

        List<Integer> merged = mergeWithDiversity(preSlice, esIds, limit);

        if (merged.size() < limit) {
            List<Integer> topRated = productRepository
                    .findTopRatedAvailable(limit * 2).stream()
                    .map(Product::getId)
                    .filter(pid -> !seenProducts.contains(pid) && !merged.contains(pid))
                    .limit(limit - merged.size())
                    .collect(Collectors.toList());
            merged.addAll(topRated);
        }

        return buildDTOs(merged, userId);
    }

    @Override
    public List<ProductRecommendDTO> getSimilar(Integer productId, int limit) {
        List<Integer> similarIds = callESKNN(
                productId,
                Set.of(productId),
                limit * 3,
                limit
        );

        if (similarIds.isEmpty()) {
            return productRepository
                    .findSimilarByCategory(productId, limit).stream()
                    .map(p -> ProductRecommendDTO.builder()
                            .id(p.getId())
                            .name(p.getName())
                            .price(p.getEffectivePrice())
                            .discountPrice(p.getDiscountPrice())
                            .rating(p.getRating())
                            .totalReviews(p.getTotalReviews())
                            .imageUrls(p.getImageUrls())
                            .shopId(p.getShop().getId())
                            .shopName(p.getShop().getShopName())
                            .categoryName(p.getCategory().getName())
                            .recommendReason("Cùng danh mục")
                            .build())
                    .collect(Collectors.toList());
        }

        return buildDTOs(similarIds, null);
    }

    // ====== PRIVATE METHODS (GIỮ NGUYÊN) ======

    private List<Integer> getRealtimeFromES(
            Integer userId,
            Set<Integer> seenProducts,
            List<Integer> alreadyIncluded,
            int count
    ) {
        try {
            List<Integer> recentIds = orderItemRepository.findRecentProductIdsByUser(userId, 3);
            List<Integer> wishIds = wishlistRepository.findRecentProductIdsByUser(userId, 2);

            List<Integer> seedIds = new ArrayList<>(recentIds);
            seedIds.addAll(wishIds);
            if (seedIds.isEmpty()) return List.of();

            Set<Integer> excludeIds = new HashSet<>(seenProducts);
            excludeIds.addAll(alreadyIncluded);

            return callESKNN(seedIds.get(0), excludeIds, count * 3, count);
        } catch (Exception e) {
            log.warn("ES realtime failed: {}", e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Integer> callESKNN(
            Integer seedId,
            Set<Integer> excludeIds,
            int numCandidates,
            int k
    ) {
        try {
            RestTemplate rt = new RestTemplate();

            Map<String, Object> mustNot = Map.of(
                    "terms", Map.of("product_id", new ArrayList<>(excludeIds))
            );

            Map<String, Object> knn = new LinkedHashMap<>();
            knn.put("field", "embedding");
            knn.put("query_vector_id", String.valueOf(seedId));
            knn.put("k", k);
            knn.put("num_candidates", numCandidates);
            knn.put("filter", Map.of("bool", Map.of("must_not", mustNot)));

            var resp = rt.postForEntity(
                    ES_URL + "/" + ES_INDEX + "/_search",
                    Map.of("knn", knn, "size", k),
                    Map.class
            );

            if (resp.getBody() == null) return List.of();

            List<Map<String, Object>> hits = (List<Map<String, Object>>)
                    ((Map<String, Object>) resp.getBody().get("hits")).get("hits");

            return hits.stream()
                    .map(h -> (Integer) ((Map<String, Object>) h.get("_source")).get("product_id"))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("ES KNN error: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Integer> mergeWithDiversity(List<Integer> preIds, List<Integer> esIds, int limit) {
        List<Integer> result = new ArrayList<>();
        Map<Integer, Integer> shopCount = new HashMap<>();
        int pi = 0, ei = 0;

        while (result.size() < limit && (pi < preIds.size() || ei < esIds.size())) {

            for (int i = 0; i < 2 && pi < preIds.size() && result.size() < limit; i++) {
                Integer pid = preIds.get(pi++);
                Integer shopId = getShopId(pid);
                if (shopCount.getOrDefault(shopId, 0) < 2) {
                    result.add(pid);
                    shopCount.merge(shopId, 1, Integer::sum);
                }
            }

            if (ei < esIds.size() && result.size() < limit) {
                Integer pid = esIds.get(ei++);
                if (!result.contains(pid)) {
                    Integer shopId = getShopId(pid);
                    if (shopCount.getOrDefault(shopId, 0) < 2) {
                        result.add(pid);
                        shopCount.merge(shopId, 1, Integer::sum);
                    }
                }
            }
        }
        return result;
    }

    private List<ProductRecommendDTO> buildDTOs(List<Integer> productIds, Integer userId) {
        if (productIds.isEmpty()) return List.of();

        List<Product> products = productRepository.findAllById(productIds);
        Map<Integer, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        String favoriteName = userId != null
                ? orderItemRepository.findMostBoughtProductName(userId)
                : null;

        return productIds.stream()
                .filter(productMap::containsKey)
                .map(pid -> {
                    Product p = productMap.get(pid);
                    return ProductRecommendDTO.builder()
                            .id(p.getId())
                            .name(p.getName())
                            .price(p.getEffectivePrice())
                            .discountPrice(p.getDiscountPrice())
                            .rating(p.getRating())
                            .totalReviews(p.getTotalReviews())
                            .imageUrls(p.getImageUrls())
                            .shopId(p.getShop().getId())
                            .shopName(p.getShop().getShopName())
                            .categoryName(p.getCategory().getName())
                            .recommendReason(buildReason(p, favoriteName))
                            .build();
                })
                .collect(Collectors.toList());
    }

    private String buildReason(Product p, String favoriteName) {
        if (favoriteName != null && !favoriteName.isBlank()) {
            String keyword = favoriteName.split(" ")[0].toLowerCase();
            if (p.getName().toLowerCase().contains(keyword)) {
                return "Vì bạn hay mua " + favoriteName;
            }
            // Parse ingredients JSON array
            List<String> ingredients = parseJsonArray(p.getIngredients());
            if (!ingredients.isEmpty()) {
                String ingredientsText = String.join(" ", ingredients).toLowerCase();
                if (ingredientsText.contains(keyword)) {
                    return "Nguyên liệu tương tự " + favoriteName;
                }
            }
        }
        if (p.getRating() >= 4.5 && p.getTotalReviews() >= 10) {
            return "Được đánh giá cao";
        }
        // Parse tags JSON array
        List<String> tags = parseJsonArray(p.getTags());
        if (tags.contains("bestseller")) {
            return "Bán chạy nhất";
        }
        if (tags.contains("healthy")) {
            return "Lựa chọn healthy";
        }
        return "Có thể bạn sẽ thích";
    }

    /**
     * Helper: Parse JSON array string to List<String>
     */
    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse JSON array: {}", json);
            return List.of();
        }
    }

    private Integer getShopId(Integer productId) {
        return productShopCache.computeIfAbsent(productId, pid ->
                productRepository.findById(pid)
                        .map(p -> p.getShop().getId())
                        .orElse(-1)
        );
    }

    private Set<Integer> getSeenProductIds(Integer userId) {
        Set<Integer> seen = new HashSet<>();
        seen.addAll(orderItemRepository.findProductIdsByUser(userId));
        seen.addAll(wishlistRepository.findProductIdsByUser(userId));
        return seen;
    }
}