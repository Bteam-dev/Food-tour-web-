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
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class FoodVectorServiceImpl implements FoodVectorService {

    private static final Logger log = LoggerFactory.getLogger(FoodVectorServiceImpl.class);

    private EmbeddingModel embeddingModel;
    private ElasticsearchClient esClient;
    private final ProductRepository productRepository;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

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

            // Tạo index nếu chưa có
            ensureIndexExists();

            log.info("FoodVectorService initialized. Models will be synced on first use.");
        } catch (Exception e) {
            log.error("FoodVectorService init failed: {}", e.getMessage());
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

    private void ensureSynced() {
        if (initialized.compareAndSet(false, true)) {
            syncAllProducts();
        }
    }

    @Override
    public void syncAllProducts() {
        if (embeddingModel == null || esClient == null) {
            log.warn("syncAllProducts skipped: not initialized.");
            return;
        }
        try {
            List<Product> products = productRepository.findAll();
            if (products.isEmpty()) return;

            List<TextSegment> segments = new ArrayList<>();
            for (Product p : products) {
                String text = String.format(
                        "Món: %s. Quán: %s. Mô tả: %s. Nguyên liệu: %s. Tags: %s. Giá: %s. Rating: %.1f (%d đánh giá).",
                        p.getName(), p.getShop().getShopName(), p.getDescription(), p.getIngredients(), p.getTags(),
                        p.getEffectivePrice(), p.getRating(), p.getTotalReviews()
                );
                segments.add(TextSegment.from(text, Metadata.from("product_id", p.getId().toString())));
            }

            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

            // Bulk index vào ES
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
            log.info("Synced {} products to vector store.", segments.size());
        } catch (Exception e) {
            initialized.set(false);
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
            String text = String.format(
                    "Món: %s. Quán: %s. Mô tả: %s. Nguyên liệu: %s. Tags: %s. Giá: %s. Rating: %.1f (%d đánh giá).",
                    product.getName(), product.getShop().getShopName(), product.getDescription(), product.getIngredients(), product.getTags(),
                    product.getEffectivePrice(), product.getRating(), product.getTotalReviews()
            );
            float[] vector = embeddingModel.embed(TextSegment.from(text)).content().vector();

            Map<String, Object> doc = new HashMap<>();
            doc.put("text", text);
            doc.put("product_id", product.getId().toString());
            doc.put("vector", vector);

            esClient.index(i -> i.index(esIndex).id(product.getId().toString()).document(doc));
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
        } catch (Exception e) {
            log.error("deleteProduct failed: {}", e.getMessage());
        }
    }

    @Override
    public ContentRetriever getContentRetriever() {
        if (esClient == null || embeddingModel == null) {
            log.warn("getContentRetriever: not initialized.");
            return query -> List.of();
        }
        ensureSynced();

        return queryObj -> {
            try {
                // Embed câu hỏi
                float[] queryVector = embeddingModel.embed(
                        TextSegment.from(queryObj.text())
                ).content().vector();

                // Dùng script_score thay vì KNN để tránh bug KnnQuery.k
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

                List<Content> results = new ArrayList<>();
                for (Hit<Map<String, Object>> hit : response.hits().hits()) {
                    double score = hit.score() != null ? hit.score() - 1.0 : 0.0;
                    if (score >= minScore && hit.source() != null) {
                        String text = (String) hit.source().get("text");
                        results.add(Content.from(TextSegment.from(text)));
                    }
                }
                return results;
            } catch (Exception e) {
                log.error("ContentRetriever search failed: {}", e.getMessage());
                return List.of();
            }
        };
    }
}
