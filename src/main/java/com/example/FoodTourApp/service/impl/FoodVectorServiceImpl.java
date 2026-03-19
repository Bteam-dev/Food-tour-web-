package com.example.FoodTourApp.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.example.FoodTourApp.entity.Product;
import com.example.FoodTourApp.repository.ProductRepository;
import com.example.FoodTourApp.service.FoodVectorService;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import jakarta.annotation.PostConstruct;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class FoodVectorServiceImpl implements FoodVectorService {

    private static final Logger log = LoggerFactory.getLogger(FoodVectorServiceImpl.class);

    private EmbeddingModel embeddingModel;
    private ElasticsearchClient esClient;
    private final ProductRepository productRepository;

    // Background thread để sync không block startup
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();

    @Value("${ollama.base-url}")
    private String ollamaBaseUrl;

    @Value("${ollama.embedding-model}")
    private String embeddingModelName;

    @Value("${elasticsearch.host}")
    private String esHost;

    @Value("${elasticsearch.port}")
    private int esPort;

    @Value("${elasticsearch.index}")
    private String esIndex;

    @Value("${rag.max-results}")
    private int maxResults;

    @Value("${rag.min-score}")
    private double minScore;

    // Khởi tạo 1 lần trong @PostConstruct, tái sử dụng mãi
    private ContentRetriever contentRetriever;

    public FoodVectorServiceImpl(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @PostConstruct
    public void init() {
        try {
            this.embeddingModel = OllamaEmbeddingModel.builder()
                    .baseUrl(ollamaBaseUrl)
                    .modelName(embeddingModelName)
                    .timeout(Duration.ofSeconds(60))
                    .build();

            RestClient restClient = RestClient.builder(new HttpHost(esHost, esPort, "http")).build();
            RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
            this.esClient = new ElasticsearchClient(transport);

            ensureIndexExists();

            // Khởi tạo ContentRetriever 1 lần duy nhất
            this.contentRetriever = buildContentRetriever();

            // Chạy background: check ES có data chưa
            // Nếu ES đã có data (kể cả sau restart) → SKIP, không sync lại
            // Nếu ES trống (lần đầu setup hoặc xóa ElasticSearch_data) → sync toàn bộ
            backgroundExecutor.submit(this::checkAndSyncIfNeeded);

            log.info("FoodVectorService initialized.");
        } catch (Exception e) {
            log.error("FoodVectorService init failed: {}", e.getMessage());
        }
    }

    /**
     * Kiểm tra ES có data chưa.
     * - Có data → skip (dù app restart bao nhiêu lần cũng không sync lại)
     * - Không có data → sync toàn bộ 1 lần duy nhất (lần đầu setup)
     *
     * Source of truth là ES trên disk, không phải AtomicBoolean trong RAM.
     */
    private void checkAndSyncIfNeeded() {
        try {
            long count = esClient.count(c -> c.index(esIndex)).count();
            if (count == 0) {
                log.info("ES index is empty → syncing all products for the first time...");
                syncAllProducts();
            } else {
                log.info("ES already has {} products → skip sync. Event-driven sync handles updates.", count);
            }
        } catch (Exception e) {
            log.error("checkAndSyncIfNeeded failed: {}", e.getMessage());
        }
    }

    private void ensureIndexExists() {
        try {
            boolean exists = esClient.indices().exists(e -> e.index(esIndex)).value();
            if (!exists) {
                String mapping = """
                    {
                      "mappings": {
                        "properties": {
                          "text": { "type": "text" },
                          "product_id": { "type": "keyword" },
                          "vector": { "type": "dense_vector", "dims": 768, "index": false }
                        }
                      }
                    }
                    """;
                esClient.indices().create(CreateIndexRequest.of(c -> c
                        .index(esIndex)
                        .withJson(new StringReader(mapping))
                ));
                log.info("Created ES index: {}", esIndex);
            }
        } catch (Exception e) {
            log.warn("ensureIndexExists failed: {}", e.getMessage());
        }
    }

    @Override
    public void syncAllProducts() {
        if (embeddingModel == null || esClient == null) {
            log.warn("syncAllProducts skipped: not initialized.");
            return;
        }
        try {
            // CHỈ lấy products có isAvailable = true (không lấy soft-deleted)
            List<Product> products = productRepository.findAll().stream()
                    .filter(p -> p.getIsAvailable() != null && p.getIsAvailable())
                    .toList();
            
            if (products.isEmpty()) {
                log.info("No available products to sync.");
                return;
            }

            List<TextSegment> segments = new ArrayList<>();
            for (Product p : products) {
                String text = buildProductText(p);
                segments.add(TextSegment.from(text, Metadata.from("product_id", p.getId().toString())));
            }

            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

            List<BulkOperation> ops = new ArrayList<>();
            for (int i = 0; i < segments.size(); i++) {
                String productId = segments.get(i).metadata().getString("product_id");
                String text = segments.get(i).text();
                float[] vector = embeddings.get(i).vector();

                Map<String, Object> doc = new HashMap<>();
                doc.put("text", text);
                doc.put("product_id", productId);
                doc.put("vector", vector);

                ops.add(BulkOperation.of(b -> b.index(id -> id
                        .index(esIndex)
                        .id(productId)
                        .document(doc)
                )));
            }

            esClient.bulk(BulkRequest.of(b -> b.operations(ops)));
            log.info("Synced {} available products to vector store.", segments.size());
        } catch (Exception e) {
            log.error("syncAllProducts failed: {}", e.getMessage());
        }
    }

    @Override
    public void syncProduct(Product product) {
        if (embeddingModel == null || esClient == null) {
            log.warn("syncProduct skipped: not initialized.");
            return;
        }
        try {
            // Nếu product bị soft-delete (isAvailable = false), XÓA khỏi ES thay vì index
            if (product.getIsAvailable() == null || !product.getIsAvailable()) {
                deleteProduct(product.getId());
                log.info("Product [{}] is unavailable → deleted from ES.", product.getId());
                return;
            }
            
            // Nếu product available, index/update vào ES
            String text = buildProductText(product);
            float[] vector = embeddingModel.embed(TextSegment.from(text)).content().vector();

            Map<String, Object> doc = new HashMap<>();
            doc.put("text", text);
            doc.put("product_id", product.getId().toString());
            doc.put("vector", vector);

            esClient.index(i -> i.index(esIndex).id(product.getId().toString()).document(doc));
            log.info("Synced available product [{}] to ES.", product.getId());
        } catch (Exception e) {
            log.error("syncProduct failed: {}", e.getMessage());
        }
    }

    @Override
    public void deleteProduct(Integer productId) {
        if (esClient == null) {
            log.warn("deleteProduct skipped: not initialized.");
            return;
        }
        try {
            esClient.delete(d -> d.index(esIndex).id(productId.toString()));
            log.info("Deleted product [{}] from ES.", productId);
        } catch (Exception e) {
            log.error("deleteProduct failed: {}", e.getMessage());
        }
    }

    @Override
    public ContentRetriever getContentRetriever() {
        if (contentRetriever == null) {
            log.warn("getContentRetriever: not initialized.");
            return query -> List.of();
        }
        return contentRetriever; // trả về instance cố định, không tạo mới
    }
    
    @Override
    public List<Integer> retrieveProductIds(String query) {
        if (embeddingModel == null || esClient == null) {
            log.warn("retrieveProductIds skipped: not initialized.");
            return List.of();
        }
        
        try {
            log.info("🔍 retrieveProductIds called with query: {}", query);
            
            // Embed query và tìm products tương tự
            float[] queryVector = embeddingModel.embed(
                    TextSegment.from(query)
            ).content().vector();

            String vectorJson = Arrays.toString(queryVector);
            String queryBody = String.format("""
                {
                  "size": %d,
                  "query": {
                    "script_score": {
                      "query": { "match_all": {} },
                      "script": {
                        "source": "cosineSimilarity(params.query_vector, 'vector') + 1.0",
                        "params": { "query_vector": %s }
                      }
                    }
                  }
                }
                """, maxResults, vectorJson);

            SearchResponse<Map<String, Object>> response = esClient.search(
                    s -> s.index(esIndex).withJson(new StringReader(queryBody)),
                    (Class<Map<String, Object>>) (Class<?>) Map.class
            );

            log.info("📊 ES returned {} hits", response.hits().hits().size());
            
            // Extract product IDs từ kết quả
            List<Integer> productIds = new ArrayList<>();
            for (Hit<Map<String, Object>> hit : response.hits().hits()) {
                double score = hit.score() != null ? hit.score() - 1.0 : 0.0;
                log.info("   Hit: product_id={}, score={}, minScore={}", 
                         hit.source() != null ? hit.source().get("product_id") : "null", 
                         score, minScore);
                
                if (score >= minScore && hit.source() != null) {
                    String productIdStr = (String) hit.source().get("product_id");
                    if (productIdStr != null) {
                        productIds.add(Integer.parseInt(productIdStr));
                        log.info("   ✅ Added product ID: {}", productIdStr);
                    }
                }
            }
            
            log.info("✅ retrieveProductIds returning {} product IDs: {}", productIds.size(), productIds);
            return productIds;
        } catch (Exception e) {
            log.error("❌ retrieveProductIds failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Build ContentRetriever 1 lần duy nhất.
     * Mỗi lần user chat: embed câu hỏi → cosine similarity search ES → trả về text segments
     * LangChain4j sẽ tự inject text segments này vào prompt dưới dạng CONTEXT (RAG)
     */
    private ContentRetriever buildContentRetriever() {
        return queryObj -> {
            try {
                // Embed câu hỏi thành vector (cùng model với lúc indexing sản phẩm)
                float[] queryVector = embeddingModel.embed(
                        TextSegment.from(queryObj.text())
                ).content().vector();

                // Tìm sản phẩm gần nghĩa nhất bằng cosine similarity
                String vectorJson = Arrays.toString(queryVector);
                String queryBody = String.format("""
                    {
                      "size": %d,
                      "query": {
                        "script_score": {
                          "query": { "match_all": {} },
                          "script": {
                            "source": "cosineSimilarity(params.query_vector, 'vector') + 1.0",
                            "params": { "query_vector": %s }
                          }
                        }
                      }
                    }
                    """, maxResults, vectorJson);

                SearchResponse<Map<String, Object>> response = esClient.search(
                        s -> s.index(esIndex).withJson(new StringReader(queryBody)),
                        (Class<Map<String, Object>>) (Class<?>) Map.class
                );

                // Lọc theo minScore và trả về text thô
                // LangChain4j sẽ tự inject các text này vào prompt dưới dạng CONTEXT
                // QUAN TRỌNG: Thêm product_id vào metadata để extract navigation URLs
                List<Content> results = new ArrayList<>();
                for (Hit<Map<String, Object>> hit : response.hits().hits()) {
                    double score = hit.score() != null ? hit.score() - 1.0 : 0.0;
                    if (score >= minScore && hit.source() != null) {
                        String text = (String) hit.source().get("text");
                        String productId = (String) hit.source().get("product_id");
                        
                        // Lưu product_id vào metadata để sau này extract navigation URL
                        Metadata metadata = Metadata.from("product_id", productId);
                        results.add(Content.from(TextSegment.from(text, metadata)));
                    }
                }
                return results;
            } catch (Exception e) {
                log.error("ContentRetriever search failed: {}", e.getMessage());
                return List.of();
            }
        };
    }

    /**
     * Tách ra method riêng để tránh duplicate code giữa syncAllProducts và syncProduct
     * Build full text bao gồm TẤT CẢ thông tin quan trọng để chatbot có thể suy luận
     */
    private String buildProductText(Product p) {
        return String.format(
                "Món: %s. Quán: %s (shopId: %d). Danh mục: %s. Mô tả: %s. Nguyên liệu: %s. Tags: %s. " +
                "Dinh dưỡng: %s. Thời gian chuẩn bị: %d phút. Giá: %s VNĐ. Rating: %.1f/5.0 (%d đánh giá).",
                p.getName(),
                p.getShop().getShopName(),
                p.getShop().getId(),
                p.getCategory().getName(),
                p.getDescription(),
                p.getIngredients(),
                p.getTags() != null ? p.getTags() : "không có",
                p.getNutritionInfo() != null ? p.getNutritionInfo() : "không có",
                p.getPreparationTime() != null ? p.getPreparationTime() : 0,
                p.getEffectivePrice(),
                p.getRating(),
                p.getTotalReviews()
        );
    }
}
