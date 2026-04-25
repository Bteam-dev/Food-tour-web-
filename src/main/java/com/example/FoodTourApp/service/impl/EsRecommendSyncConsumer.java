package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.entity.Product;
import com.example.FoodTourApp.event.ProductSyncEvent;
import com.example.FoodTourApp.repository.ProductRepository;
import com.example.FoodTourApp.service.RealTimeRecommendationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Consumer xử lý recommend sync events từ Redis queue.
 *
 * Khi product được tạo/cập nhật:
 *   → Lấy categoryAverage embedding (proxy tốt nhất không cần retrain)
 *   → Upsert vào products_recommend ES index để xuất hiện trong KNN ngay
 *   → Cập nhật in-memory maps trong RealTimeRecommendationService
 *
 * Khi product bị xóa:
 *   → Xóa khỏi products_recommend ES
 *   → Xóa khỏi in-memory maps
 *
 * Embedding quality progression:
 *   1. Sau consumer này: category_average (ngay lập tức, đủ để KNN hoạt động)
 *   2. Sau index_new_products.py: text_only (chạy thủ công hoặc định kỳ)
 *   3. Sau retrain + sync_es_rcm_behavior.py: full Two-Tower (chính xác nhất)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EsRecommendSyncConsumer {

    private static final String REDIS_QUEUE_KEY = EsRecommendSyncProducerImpl.REDIS_QUEUE_KEY;
    private static final String ES_URL = "http://localhost:9200";
    private static final String ES_INDEX = "products_recommend";
    private static final int BATCH_SIZE = 50;

    private final RedisTemplate<String, ProductSyncEvent> redisTemplate;
    private final ProductRepository productRepository;
    private final RealTimeRecommendationService recommendationService;
    private final ObjectMapper objectMapper;

    private final RestTemplate restTemplate = new RestTemplate();

    @Scheduled(fixedDelay = 5000)
    public void processBatch() {
        // Drain queue theo batch
        List<ProductSyncEvent> batch = new ArrayList<>();
        for (int i = 0; i < BATCH_SIZE; i++) {
            ProductSyncEvent event = redisTemplate.opsForList().leftPop(REDIS_QUEUE_KEY);
            if (event == null) break;
            batch.add(event);
        }
        if (batch.isEmpty()) return;

        // Deduplicate: cùng product ID → chỉ giữ event mới nhất
        Map<Integer, ProductSyncEvent> deduped = new LinkedHashMap<>();
        for (ProductSyncEvent event : batch) {
            deduped.put(event.getProductId(), event);
        }

        List<Integer> toIndex  = new ArrayList<>();
        List<Integer> toDelete = new ArrayList<>();
        for (Map.Entry<Integer, ProductSyncEvent> entry : deduped.entrySet()) {
            if (entry.getValue().getAction() == ProductSyncEvent.Action.INDEX) {
                toIndex.add(entry.getKey());
            } else {
                toDelete.add(entry.getKey());
            }
        }

        if (!toIndex.isEmpty())  indexProducts(toIndex);
        if (!toDelete.isEmpty()) deleteProducts(toDelete);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INDEX
    // ─────────────────────────────────────────────────────────────────────────

    private void indexProducts(List<Integer> productIds) {
        List<Product> products = productRepository.findAllById(productIds);
        if (products.isEmpty()) return;

        StringBuilder bulk = new StringBuilder();
        int queued = 0;

        for (Product product : products) {
            Integer categoryId = product.getCategory() != null ? product.getCategory().getId() : null;
            Integer shopId     = product.getShop()     != null ? product.getShop().getId()     : null;

            float[] embedding = recommendationService.getCategoryEmbedding(categoryId);
            if (embedding == null) {
                // Chưa có category average (category mới chưa có products nào) → bỏ qua ES,
                // chỉ cập nhật metadata maps để sau này rerank dùng được
                recommendationService.registerProduct(product.getId(), categoryId, shopId, null);
                log.debug("No category embedding for product {} (category {}), skipped ES index", product.getId(), categoryId);
                continue;
            }

            try {
                Map<String, Object> action = Map.of(
                        "index", Map.of("_index", ES_INDEX, "_id", String.valueOf(product.getId())));
                Map<String, Object> doc = buildDocument(product, categoryId, shopId, embedding);

                bulk.append(objectMapper.writeValueAsString(action)).append("\n");
                bulk.append(objectMapper.writeValueAsString(doc)).append("\n");
                queued++;
            } catch (Exception e) {
                log.error("Failed to serialize product {} for recommend ES: {}", product.getId(), e.getMessage());
                continue;
            }

            // Cập nhật in-memory ngay lập tức (không chờ scheduledRefreshEmbeddings)
            recommendationService.registerProduct(product.getId(), categoryId, shopId, embedding);
        }

        if (queued == 0) return;

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.valueOf("application/x-ndjson"));
            var response = restTemplate.postForEntity(
                    ES_URL + "/_bulk",
                    new HttpEntity<>(bulk.toString(), headers),
                    Map.class
            );

            boolean hasErrors = Boolean.TRUE.equals(
                    response.getBody() != null ? response.getBody().get("errors") : true);
            if (hasErrors) {
                log.warn("Recommend bulk index had some errors for {} products", queued);
            } else {
                log.info("Recommend sync: indexed {} products into {} (embedding_source=category_average)",
                        queued, ES_INDEX);
            }
        } catch (Exception e) {
            log.error("Recommend bulk index failed: {}", e.getMessage());
        }
    }

    private Map<String, Object> buildDocument(Product product, Integer categoryId, Integer shopId, float[] embedding) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("product_id",       product.getId());
        doc.put("name",             product.getName());
        doc.put("embedding",        toList(embedding));
        doc.put("embedding_source", "category_average"); // đánh dấu để biết chưa phải Two-Tower thật
        if (categoryId != null) doc.put("category_id", categoryId);
        if (shopId     != null) doc.put("shop_id",     shopId);
        if (product.getRating() != null) doc.put("rating", product.getRating());
        return doc;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE
    // ─────────────────────────────────────────────────────────────────────────

    private void deleteProducts(List<Integer> productIds) {
        StringBuilder bulk = new StringBuilder();
        for (Integer id : productIds) {
            try {
                Map<String, Object> action = Map.of(
                        "delete", Map.of("_index", ES_INDEX, "_id", String.valueOf(id)));
                bulk.append(objectMapper.writeValueAsString(action)).append("\n");
            } catch (Exception e) {
                log.error("Failed to serialize delete action for product {}: {}", id, e.getMessage());
            }
            recommendationService.removeProduct(id);
        }

        if (bulk.isEmpty()) return;

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.valueOf("application/x-ndjson"));
            restTemplate.postForEntity(
                    ES_URL + "/_bulk",
                    new HttpEntity<>(bulk.toString(), headers),
                    Map.class
            );
            log.info("Recommend sync: deleted {} products from {}", productIds.size(), ES_INDEX);
        } catch (Exception e) {
            log.error("Recommend bulk delete failed: {}", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private List<Float> toList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float f : arr) list.add(f);
        return list;
    }
}
