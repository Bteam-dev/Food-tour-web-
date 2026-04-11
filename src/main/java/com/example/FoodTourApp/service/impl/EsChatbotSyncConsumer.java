package com.example.FoodTourApp.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.DeleteOperation;
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.example.FoodTourApp.entity.*;
import com.example.FoodTourApp.event.ProductSyncEvent;
import com.example.FoodTourApp.repository.ProductRepository;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Background consumer for syncing products to Elasticsearch CHATBOT index with embeddings.
 * 
 * Flow:
 * 1. Poll events from Redis queue (es:chatbot:sync:queue)
 * 2. Deduplicate by productId (latest action wins)
 * 3. Batch fetch products from DB
 * 4. Generate embeddings via Ollama
 * 5. Bulk index to ES (foodtour_products_chatbot)
 * 
 * Key differences from Search Consumer:
 * - Generates 768-dim embedding vectors via Ollama
 * - Creates structured context_text for RAG
 * - Indexes to chatbot index (dense_vector field)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EsChatbotSyncConsumer {
    
    private static final String REDIS_QUEUE_KEY = "es:chatbot:sync:queue";
    private static final int BATCH_SIZE = 50;
    private static final long POLL_INTERVAL_MS = 5000; // 5 seconds
    
    private final RedisTemplate<String, ProductSyncEvent> redisTemplate;
    private final ProductRepository productRepository;
    
    @Value("${elasticsearch.host:localhost}")
    private String esHost;
    
    @Value("${elasticsearch.port:9200}")
    private int esPort;
    
    @Value("${elasticsearch.index:foodtour_products_chatbot}")
    private String chatbotIndex;
    
    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;
    
    @Value("${ollama.embedding-model:nomic-embed-text}")
    private String embeddingModelName;
    
    private ElasticsearchClient esClient;
    private EmbeddingModel embeddingModel;
    private volatile boolean running = true;
    
    @PostConstruct
    public void init() {
        try {
            // Initialize Ollama embedding model
            this.embeddingModel = OllamaEmbeddingModel.builder()
                    .baseUrl(ollamaBaseUrl)
                    .modelName(embeddingModelName)
                    .timeout(Duration.ofSeconds(60))
                    .build();
            
            // Initialize ES client
            RestClient restClient = RestClient.builder(
                new HttpHost(esHost, esPort, "http")
            ).build();
            RestClientTransport transport = new RestClientTransport(
                restClient, new JacksonJsonpMapper()
            );
            this.esClient = new ElasticsearchClient(transport);
            
            log.info("EsChatbotSyncConsumer initialized - Queue: {}, Index: {}, Ollama: {}", 
                    REDIS_QUEUE_KEY, chatbotIndex, ollamaBaseUrl);
        } catch (Exception e) {
            log.error("Failed to initialize EsChatbotSyncConsumer", e);
        }
    }
    
    @PreDestroy
    public void shutdown() {
        running = false;
        log.info("EsChatbotSyncConsumer shutting down...");
    }
    
    /**
     * Background polling job - runs every 5 seconds
     */
    @Scheduled(fixedDelay = POLL_INTERVAL_MS)
    public void pollAndSync() {
        if (!running || esClient == null || embeddingModel == null) return;
        
        try {
            // Pop batch of events from queue (FIFO)
            List<ProductSyncEvent> events = new ArrayList<>();
            for (int i = 0; i < BATCH_SIZE; i++) {
                ProductSyncEvent event = redisTemplate.opsForList()
                        .leftPop(REDIS_QUEUE_KEY, 100, TimeUnit.MILLISECONDS);
                if (event == null) break;
                events.add(event);
            }
            
            if (events.isEmpty()) return;
            
            log.info("Polled {} chatbot sync events from Redis", events.size());
            
            // Deduplicate: keep latest event per productId
            Map<Integer, ProductSyncEvent> deduped = new LinkedHashMap<>();
            for (ProductSyncEvent event : events) {
                deduped.put(event.getProductId(), event);
            }
            
            log.info("After deduplication: {} unique products", deduped.size());
            
            // Process batch
            processBatch(deduped.values());
            
        } catch (Exception e) {
            log.error("Error in chatbot sync polling loop", e);
        }
    }
    
    private void processBatch(Collection<ProductSyncEvent> events) {
        if (events.isEmpty()) return;
        
        // Separate INDEX vs DELETE actions
        Set<Integer> toIndex = new HashSet<>();
        Set<Integer> toDelete = new HashSet<>();
        
        for (ProductSyncEvent event : events) {
            if (ProductSyncEvent.Action.DELETE.equals(event.getAction())) {
                toDelete.add(event.getProductId());
            } else {
                toIndex.add(event.getProductId());
            }
        }
        
        // Fetch products from DB (batch)
        List<Product> products = toIndex.isEmpty() 
                ? Collections.emptyList()
                : productRepository.findAllById(toIndex);
        
        log.info("Fetched {} products from DB, {} to delete", products.size(), toDelete.size());
        
        // Build bulk request
        List<BulkOperation> operations = new ArrayList<>();
        
        // INDEX operations with embeddings
        for (Product product : products) {
            try {
                Map<String, Object> doc = buildChatbotDocument(product);
                operations.add(BulkOperation.of(op -> op
                    .index(IndexOperation.of(idx -> idx
                        .index(chatbotIndex)
                        .id(String.valueOf(product.getId()))
                        .document(doc)
                    ))
                ));
            } catch (Exception e) {
                log.error("Failed to build chatbot doc for product {}: {}", 
                        product.getId(), e.getMessage());
            }
        }
        
        // DELETE operations
        for (Integer productId : toDelete) {
            operations.add(BulkOperation.of(op -> op
                .delete(DeleteOperation.of(del -> del
                    .index(chatbotIndex)
                    .id(String.valueOf(productId))
                ))
            ));
        }
        
        if (operations.isEmpty()) {
            log.warn("No valid operations to execute");
            return;
        }
        
        // Execute bulk request
        try {
            BulkRequest bulkRequest = BulkRequest.of(br -> br
                .operations(operations)
                .refresh(Refresh.False)
            );
            
            BulkResponse response = esClient.bulk(bulkRequest);
            
            if (response.errors()) {
                long errorCount = response.items().stream()
                        .filter(item -> item.error() != null)
                        .count();
                log.error("Bulk chatbot sync had {} errors out of {} operations", 
                        errorCount, operations.size());
            } else {
                log.info("✅ Chatbot sync completed: {} indexed, {} deleted", 
                        products.size(), toDelete.size());
            }
        } catch (Exception e) {
            log.error("Bulk chatbot sync to ES failed", e);
        }
    }
    
    /**
     * Build ES document for chatbot index (with embedding vector)
     */
    private Map<String, Object> buildChatbotDocument(Product product) {
        Map<String, Object> doc = new HashMap<>();
        
        // Basic fields
        doc.put("id", product.getId());
        doc.put("shop_id", product.getShop() != null ? product.getShop().getId() : null);
        doc.put("category_id", product.getCategory() != null ? product.getCategory().getId() : null);
        doc.put("name", product.getName());
        doc.put("description", product.getDescription());
        doc.put("price", product.getPrice());
        doc.put("discount_price", product.getDiscountPrice());
        doc.put("ingredients", product.getIngredients());
        doc.put("nutrition_info", product.getNutritionInfo());
        doc.put("preparation_time", product.getPreparationTime());
        doc.put("is_available", product.getIsAvailable());
        doc.put("rating", product.getRating());
        doc.put("total_reviews", product.getTotalReviews());
        doc.put("tags", product.getTags());
        
        // Shop & Category names
        doc.put("shop_name", product.getShop() != null ? product.getShop().getShopName() : null);
        doc.put("category_name", product.getCategory() != null ? product.getCategory().getName() : null);
        
        // Build context_text (structured format for RAG)
        String contextText = buildContextText(product);
        doc.put("context_text", contextText);
        
        // Generate embedding vector from context_text
        float[] embedding = generateEmbedding(contextText);
        doc.put("embedding", embedding);
        
        return doc;
    }
    
    /**
     * Build structured context text for RAG (matches Python format)
     */
    private String buildContextText(Product product) {
        BigDecimal price = product.getDiscountPrice() != null 
                ? product.getDiscountPrice() 
                : product.getPrice();
        
        String shopName = product.getShop() != null ? product.getShop().getShopName() : "Không rõ";
        Integer shopId = product.getShop() != null ? product.getShop().getId() : null;
        String categoryName = product.getCategory() != null ? product.getCategory().getName() : "Không rõ";
        
        return String.format("""
                ===PRODUCT===
                Món: %s
                Quán: %s (shopId: %s)
                Danh mục: %s
                Mô tả: %s
                Nguyên liệu: %s
                Tags: %s
                Dinh dưỡng: %s
                Thời gian chuẩn bị: %s phút
                Giá: %s VNĐ
                Rating: %.1f/5.0 (%d đánh giá)
                ===END_PRODUCT===""",
                product.getName(),
                shopName,
                shopId,
                categoryName,
                product.getDescription() != null ? product.getDescription() : "",
                product.getIngredients() != null ? product.getIngredients() : "",
                product.getTags() != null ? product.getTags() : "không có",
                product.getNutritionInfo() != null ? product.getNutritionInfo() : "không có",
                product.getPreparationTime() != null ? product.getPreparationTime() : 0,
                price,
                product.getRating() != null ? product.getRating() : 0.0,
                product.getTotalReviews() != null ? product.getTotalReviews() : 0
        );
    }
    
    /**
     * Generate embedding vector using Ollama (768 dimensions)
     */
    private float[] generateEmbedding(String text) {
        try {
            dev.langchain4j.data.embedding.Embedding embedding = embeddingModel
                    .embed(TextSegment.from(text))
                    .content();
            return embedding.vector();
        } catch (Exception e) {
            log.error("Failed to generate embedding, using zero vector: {}", e.getMessage());
            // Return zero vector as fallback (768 dims)
            float[] zeroVector = new float[768];
            Arrays.fill(zeroVector, 0.0f);
            return zeroVector;
        }
    }
}
