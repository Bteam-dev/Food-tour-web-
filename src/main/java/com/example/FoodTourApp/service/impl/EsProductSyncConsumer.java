package com.example.FoodTourApp.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.example.FoodTourApp.entity.Product;
import com.example.FoodTourApp.event.ProductSyncEvent;
import com.example.FoodTourApp.repository.ProductRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Consumer that processes ES sync events from Redis queue.
 * 
 * Features:
 * - Batch processing (collects events for a short window, then bulk syncs)
 * - Deduplication (if same product has multiple events, only process latest)
 * - Direct ES Java client (no Python subprocess)
 * - Graceful shutdown
 */
@Service
public class EsProductSyncConsumer {
    
    private static final Logger log = LoggerFactory.getLogger(EsProductSyncConsumer.class);
    
    private static final String SYNC_QUEUE_KEY = EsProductSyncProducerImpl.SYNC_QUEUE_KEY;
    
    /**
     * Batch settings
     */
    private static final int BATCH_SIZE = 50;           // Max events per batch
    private static final int BATCH_TIMEOUT_SECONDS = 2; // Wait time before processing smaller batch
    
    private final RedisTemplate<String, ProductSyncEvent> redisTemplate;
    private final ProductRepository productRepository;
    
    private ElasticsearchClient esClient;
    private ExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    @Value("${elasticsearch.host}")
    private String esHost;

    @Value("${elasticsearch.port}")
    private int esPort;

    @Value("${elasticsearch.search-index}")
    private String searchIndex;
    
    public EsProductSyncConsumer(
            RedisTemplate<String, ProductSyncEvent> redisTemplate,
            ProductRepository productRepository
    ) {
        this.redisTemplate = redisTemplate;
        this.productRepository = productRepository;
    }
    
    @PostConstruct
    public void start() {
        // Initialize ES client
        try {
            RestClient restClient = RestClient.builder(new HttpHost(esHost, esPort, "http")).build();
            RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
            this.esClient = new ElasticsearchClient(transport);
            log.info("ES Sync Consumer - Connected to Elasticsearch at {}:{}", esHost, esPort);
        } catch (Exception e) {
            log.error("ES Sync Consumer - Failed to connect to Elasticsearch", e);
            return;
        }
        
        // Start consumer thread
        running.set(true);
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "es-sync-consumer");
            t.setDaemon(true);
            return t;
        });
        executor.submit(this::consumeLoop);
        log.info("ES Sync Consumer started");
    }
    
    @PreDestroy
    public void stop() {
        running.set(false);
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("ES Sync Consumer stopped");
    }
    
    /**
     * Main consume loop - runs in background thread
     */
    private void consumeLoop() {
        log.info("ES Sync Consumer loop started");
        
        while (running.get()) {
            try {
                // Collect batch of events
                List<ProductSyncEvent> batch = collectBatch();
                
                if (!batch.isEmpty()) {
                    processBatch(batch);
                }
                
            } catch (Exception e) {
                log.error("ES Sync Consumer error", e);
                // Sleep briefly before retrying
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        log.info("ES Sync Consumer loop ended");
    }
    
    /**
     * Collect events from Redis queue until batch is full or timeout
     */
    private List<ProductSyncEvent> collectBatch() {
        List<ProductSyncEvent> events = new ArrayList<>();
        long deadline = System.currentTimeMillis() + (BATCH_TIMEOUT_SECONDS * 1000);
        
        while (events.size() < BATCH_SIZE && System.currentTimeMillis() < deadline) {
            try {
                // BRPOP with timeout (blocking pop from right)
                ProductSyncEvent event = redisTemplate.opsForList()
                        .rightPop(SYNC_QUEUE_KEY, Duration.ofMillis(500));
                
                if (event != null) {
                    events.add(event);
                }
            } catch (Exception e) {
                log.warn("Error popping from queue: {}", e.getMessage());
                break;
            }
        }
        
        return events;
    }
    
    /**
     * Process a batch of events - deduplicate and bulk sync to ES
     */
    private void processBatch(List<ProductSyncEvent> events) {
        log.info("Processing batch of {} events", events.size());
        
        // Deduplicate: keep only latest event per productId
        Map<Integer, ProductSyncEvent> dedupedMap = new LinkedHashMap<>();
        for (ProductSyncEvent event : events) {
            dedupedMap.put(event.getProductId(), event); // Later events overwrite earlier ones
        }
        
        // Separate into index and delete operations
        Set<Integer> toIndex = new HashSet<>();
        Set<Integer> toDelete = new HashSet<>();
        
        for (ProductSyncEvent event : dedupedMap.values()) {
            if (event.getAction() == ProductSyncEvent.Action.INDEX) {
                toIndex.add(event.getProductId());
            } else {
                toDelete.add(event.getProductId());
            }
        }
        
        // Process deletes
        if (!toDelete.isEmpty()) {
            bulkDelete(toDelete);
        }
        
        // Process indexes
        if (!toIndex.isEmpty()) {
            bulkIndex(toIndex);
        }
    }
    
    /**
     * Bulk delete documents from ES
     */
    private void bulkDelete(Set<Integer> productIds) {
        try {
            List<BulkOperation> operations = new ArrayList<>();
            
            for (Integer productId : productIds) {
                operations.add(BulkOperation.of(op -> op
                        .delete(d -> d
                                .index(searchIndex)
                                .id(String.valueOf(productId))
                        )
                ));
            }
            
            BulkRequest request = BulkRequest.of(b -> b.operations(operations));
            BulkResponse response = esClient.bulk(request);
            
            if (response.errors()) {
                for (BulkResponseItem item : response.items()) {
                    if (item.error() != null) {
                        log.warn("Delete error for product {}: {}", item.id(), item.error().reason());
                    }
                }
            }
            
            log.info("Bulk deleted {} products from ES", productIds.size());
            
        } catch (Exception e) {
            log.error("Bulk delete failed", e);
        }
    }
    
    /**
     * Bulk index documents to ES
     */
    private void bulkIndex(Set<Integer> productIds) {
        try {
            // Fetch products from DB
            List<Product> products = productRepository.findAllById(productIds);
            
            if (products.isEmpty()) {
                log.warn("No products found for IDs: {}", productIds);
                return;
            }
            
            List<BulkOperation> operations = new ArrayList<>();
            
            for (Product product : products) {
                Map<String, Object> doc = buildDocument(product);
                
                operations.add(BulkOperation.of(op -> op
                        .index(i -> i
                                .index(searchIndex)
                                .id(String.valueOf(product.getId()))
                                .document(doc)
                        )
                ));
            }
            
            BulkRequest request = BulkRequest.of(b -> b.operations(operations));
            BulkResponse response = esClient.bulk(request);
            
            if (response.errors()) {
                for (BulkResponseItem item : response.items()) {
                    if (item.error() != null) {
                        log.warn("Index error for product {}: {}", item.id(), item.error().reason());
                    }
                }
            }
            
            log.info("Bulk indexed {} products to ES", products.size());
            
        } catch (Exception e) {
            log.error("Bulk index failed", e);
        }
    }
    
    /**
     * Build ES document from Product entity
     */
    private Map<String, Object> buildDocument(Product product) {
        Map<String, Object> doc = new HashMap<>();
        
        // IDs
        doc.put("id", product.getId());
        doc.put("shop_id", product.getShop() != null ? product.getShop().getId() : null);
        doc.put("category_id", product.getCategory() != null ? product.getCategory().getId() : null);
        
        // Shop info
        if (product.getShop() != null) {
            doc.put("shop_name", product.getShop().getShopName());
            doc.put("shop_city", product.getShop().getCity() != null ? 
                    product.getShop().getCity().toLowerCase() : null);
            doc.put("shop_address", product.getShop().getAddressLine());
            doc.put("shop_is_verified", product.getShop().getIsVerified());
            doc.put("shop_is_active", product.getShop().getIsActive());
        }
        
        // Category
        if (product.getCategory() != null) {
            doc.put("category_name", product.getCategory().getName());
        }
        
        // Product fields
        doc.put("name", product.getName());
        doc.put("name_normalized", normalizeVietnamese(product.getName()));
        doc.put("description", product.getDescription());
        doc.put("description_normalized", normalizeVietnamese(product.getDescription()));
        
        // Prices
        BigDecimal price = product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO;
        BigDecimal discountPrice = product.getDiscountPrice();
        BigDecimal effectivePrice = (discountPrice != null && discountPrice.compareTo(BigDecimal.ZERO) > 0 
                && discountPrice.compareTo(price) < 0) ? discountPrice : price;
        
        doc.put("price", price.doubleValue());
        doc.put("discount_price", discountPrice != null ? discountPrice.doubleValue() : null);
        doc.put("effective_price", effectivePrice.doubleValue());
        doc.put("has_discount", discountPrice != null && discountPrice.compareTo(BigDecimal.ZERO) > 0 
                && discountPrice.compareTo(price) < 0);
        
        // Arrays (stored as JSON in entity)
        doc.put("image_urls", parseJsonArray(product.getImageUrls()));
        doc.put("ingredients", product.getIngredients());
        doc.put("ingredients_list", parseJsonArray(product.getIngredients()));
        doc.put("nutrition_info", parseJsonArray(product.getNutritionInfo()));
        doc.put("tags", parseJsonArrayLowercase(product.getTags()));
        
        // Numeric
        doc.put("preparation_time", product.getPreparationTime());
        doc.put("stock_quantity", product.getStockQuantity() != null ? product.getStockQuantity() : 0);
        doc.put("min_order_quantity", product.getMinOrderQuantity() != null ? product.getMinOrderQuantity() : 1);
        doc.put("max_order_quantity", product.getMaxOrderQuantity() != null ? product.getMaxOrderQuantity() : 999);
        
        // Status
        doc.put("is_available", product.getIsAvailable() != null && product.getIsAvailable());
        
        // Rating
        Double rating = product.getRating() != null ? product.getRating() : 0.0;
        Long totalReviews = product.getTotalReviews() != null ? product.getTotalReviews() : 0L;
        doc.put("rating", rating);
        doc.put("total_reviews", totalReviews);
        doc.put("rating_score", calculateRatingScore(rating, totalReviews));
        
        // Timestamps
        doc.put("created_at", formatDateTime(product.getCreatedAt()));
        doc.put("updated_at", formatDateTime(product.getUpdatedAt()));
        
        // Search text (combined)
        StringBuilder searchText = new StringBuilder();
        searchText.append(product.getName() != null ? product.getName() : "").append(" ");
        searchText.append(product.getDescription() != null ? product.getDescription() : "").append(" ");
        if (product.getShop() != null) {
            searchText.append(product.getShop().getShopName() != null ? product.getShop().getShopName() : "").append(" ");
        }
        if (product.getCategory() != null) {
            searchText.append(product.getCategory().getName() != null ? product.getCategory().getName() : "").append(" ");
        }
        doc.put("search_text", searchText.toString().trim());
        
        return doc;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════════
    
    private String normalizeVietnamese(String text) {
        if (text == null) return "";
        text = text.replace("Đ", "D").replace("đ", "d");
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(normalized).replaceAll("").toLowerCase();
    }
    
    @SuppressWarnings("unchecked")
    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }
    
    private List<String> parseJsonArrayLowercase(String json) {
        List<String> list = parseJsonArray(json);
        return list.stream().map(String::toLowerCase).toList();
    }
    
    private double calculateRatingScore(Double rating, Long totalReviews) {
        if (rating == null || totalReviews == null || rating == 0 || totalReviews == 0) {
            return 0.0;
        }
        return Math.round(rating * Math.log(totalReviews + 1) * 10000.0) / 10000.0;
    }
    
    private String formatDateTime(LocalDateTime dt) {
        if (dt == null) return null;
        return dt.format(DateTimeFormatter.ISO_DATE_TIME);
    }
}
