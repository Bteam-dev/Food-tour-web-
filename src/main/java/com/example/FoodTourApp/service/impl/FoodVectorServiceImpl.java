package com.example.FoodTourApp.service.impl;

import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.example.FoodTourApp.repository.ProductRepository;
import com.example.FoodTourApp.service.EsChatbotSyncProducer;
import com.example.FoodTourApp.service.FoodVectorService;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import jakarta.annotation.PostConstruct;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class FoodVectorServiceImpl implements FoodVectorService {

    private static final Logger log = LoggerFactory.getLogger(FoodVectorServiceImpl.class);

    /**
     * ThreadLocal cache: lưu product IDs từ lần RAG gần nhất
     * Để ChatbotMessageServiceImpl lấy product IDs mà KHÔNG cần gọi ES lần 2
     * ThreadLocal đảm bảo thread-safe giữa các request đồng thời
     */
    private final ThreadLocal<List<Integer>> lastRetrievedProductIds = new ThreadLocal<>();

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EsChatbotSyncProducer chatbotSyncProducer;

    private EmbeddingModel embeddingModel;
    private ElasticsearchClient esClient;

    @Value("${gemini.api-key}")
    private String geminiApiKey;

    @Value("${gemini.embedding-model}")
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

    private ContentRetriever contentRetriever;

    @PostConstruct
    public void init() {
        try {
            this.embeddingModel = GoogleAiEmbeddingModel.builder()
                    .apiKey(geminiApiKey)
                    .modelName(embeddingModelName)
                    .build();

            RestClient restClient = RestClient.builder(new HttpHost(esHost, esPort, "http")).build();
            RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
            this.esClient = new ElasticsearchClient(transport);

            this.contentRetriever = buildContentRetriever();

            log.info("FoodVectorService initialized with Google Embedding + index: {}", esIndex);
        } catch (Exception e) {
            log.error("FoodVectorService init failed", e);
        }
    }

    @Override
    public ContentRetriever getContentRetriever() {
        return contentRetriever != null ? contentRetriever : query -> List.of();
    }

    /**
     * Lấy product IDs từ cache (đã được RAG ContentRetriever populate)
     * Nếu cache rỗng (edge case) mới fallback gọi ES
     * → Tiết kiệm 1 lần embedding + 1 lần ES query mỗi message
     */
    @Override
    public List<Integer> retrieveProductIds(String query) {
        // Ưu tiên lấy từ cache (đã được buildContentRetriever() set)
        List<Integer> cached = lastRetrievedProductIds.get();
        if (cached != null && !cached.isEmpty()) {
            lastRetrievedProductIds.remove(); // Cleanup ThreadLocal
            log.info("[RAG] Using cached product IDs: {} (skipped duplicate ES call)", cached);
            return cached;
        }

        // Fallback: gọi ES trực tiếp (chỉ khi cache miss)
        log.info("[RAG] Cache miss, querying ES directly for: '{}'", query);
        try {
            float[] queryVector = embeddingModel.embed(TextSegment.from(query)).content().vector();
            String vectorJson = Arrays.toString(queryVector);
            String escapedQuery = query.replace("\\", "\\\\").replace("\"", "\\\"");

            String queryBody = String.format("""
                {
                  "size": %d,
                  "query": {
                    "script_score": {
                      "query": {
                        "bool": {
                          "should": [
                            { "multi_match": {
                                "query": "%s",
                                "fields": ["name^3", "description", "tags", "ingredients", "category_name^2", "shop_name", "shop_district^2", "shop_city^2", "shop_ward"],
                                "type": "best_fields",
                                "minimum_should_match": "30%%"
                            }},
                            { "match_all": {} }
                          ]
                        }
                      },
                      "script": {
                        "source": "double bm25 = _score; double cosine = cosineSimilarity(params.query_vector, 'embedding') + 1.0; return bm25 * 0.3 + cosine * 0.7;",
                        "params": { "query_vector": %s }
                      }
                    }
                  }
                }
                """, maxResults, escapedQuery, vectorJson);

            SearchResponse<Map<String, Object>> response = esClient.search(
                    s -> s.index(esIndex).withJson(new StringReader(queryBody)),
                    (Class<Map<String, Object>>) (Class<?>) Map.class
            );

            List<Integer> ids = new ArrayList<>();
            for (Hit<Map<String, Object>> hit : response.hits().hits()) {
                double score = hit.score() != null ? hit.score() : 0.0;
                if (score >= minScore && hit.source() != null) {
                    Object idObj = hit.source().get("id");
                    if (idObj != null) ids.add(Integer.valueOf(idObj.toString()));
                }
            }
            return ids;
        } catch (Exception e) {
            log.error("retrieveProductIds failed", e);
            return List.of();
        }
    }

    /**
     * Full resync: push tất cả product IDs vào Redis queue.
     * EsChatbotSyncConsumer sẽ xử lý async: generate Gemini embedding + bulk index vào ES.
     * Quá trình không blocking - Admin có thể kiểm tra progress qua server logs.
     */
    @Override
    public void fullSyncToEs() {
        try {
            List<Integer> allProductIds = productRepository.findAll()
                    .stream()
                    .map(p -> p.getId())
                    .toList();

            for (Integer productId : allProductIds) {
                chatbotSyncProducer.pushIndexEvent(productId);
            }

            log.info("Full chatbot resync triggered: pushed {} product IDs to queue. " +
                     "EsChatbotSyncConsumer will process in ~5s batches.", allProductIds.size());
        } catch (Exception e) {
            log.error("Failed to trigger full chatbot resync", e);
            throw new RuntimeException("Full resync failed: " + e.getMessage(), e);
        }
    }

    /**
     * Hybrid Search: BM25 text match + Vector cosine similarity
     *
     * Tại sao hybrid tốt hơn pure vector?
     * - Vector search: giỏi tìm semantic ("ăn gì trời lạnh" → phở, bún bò)
     * - BM25 text match: giỏi tìm exact match ("phở bò" → chính xác món phở bò)
     * - Kết hợp cả 2 = vừa hiểu ngữ nghĩa, vừa chính xác từ khóa
     */
    private ContentRetriever buildContentRetriever() {
        return queryObj -> {
            try {
                String userQuery = queryObj.text();
                log.info("[RAG] User query: '{}'", userQuery);

                float[] queryVector = embeddingModel.embed(TextSegment.from(userQuery)).content().vector();
                String vectorJson = Arrays.toString(queryVector);

                // Escape special characters for JSON
                String escapedQuery = userQuery.replace("\\", "\\\\").replace("\"", "\\\"");

                // Hybrid query: BM25 (text match) + Vector (cosine similarity)
                // - BM25 match trên name, description, tags, ingredients, category_name (boost cho name)
                // - Vector cosine similarity trên embedding
                // - script_score kết hợp: bm25_score * 0.3 + cosine * 0.7
                String queryBody = String.format("""
                {
                  "size": %d,
                  "query": {
                    "script_score": {
                      "query": {
                        "bool": {
                          "should": [
                            { "multi_match": {
                                "query": "%s",
                                "fields": ["name^3", "description", "tags", "ingredients", "category_name^2", "shop_name", "shop_district^2", "shop_city^2", "shop_ward"],
                                "type": "best_fields",
                                "minimum_should_match": "30%%"
                            }},
                            { "match_all": {} }
                          ]
                        }
                      },
                      "script": {
                        "source": "double bm25 = _score; double cosine = cosineSimilarity(params.query_vector, 'embedding') + 1.0; return bm25 * 0.3 + cosine * 0.7;",
                        "params": { "query_vector": %s }
                      }
                    }
                  }
                }
                """, maxResults, escapedQuery, vectorJson);

                SearchResponse<Map<String, Object>> response = esClient.search(
                        s -> s.index(esIndex).withJson(new StringReader(queryBody)),
                        (Class<Map<String, Object>>) (Class<?>) Map.class
                );

                List<Content> results = new ArrayList<>();
                // Cache product IDs cho reuse (tránh double embedding)
                List<Integer> cachedIds = new ArrayList<>();

                log.info("[RAG] Found {} hits from Elasticsearch", response.hits().hits().size());

                for (Hit<Map<String, Object>> hit : response.hits().hits()) {
                    double rawScore = hit.score() != null ? hit.score() : 0.0;
                    // Hybrid score = bm25 * 0.3 + (cosine + 1.0) * 0.7
                    // Minimum possible = 0 * 0.3 + (0 + 1.0) * 0.7 = 0.7
                    double normalizedScore = rawScore;

                    String contextText = (String) hit.source().get("context_text");
                    Object idObj = hit.source().get("id");
                    String productName = extractProductName(contextText);

                    log.info("   -> Product ID: {} | Name: '{}' | Hybrid score: {}", idObj, productName, rawScore);

                    if (normalizedScore >= minScore && contextText != null && idObj != null) {
                        Metadata metadata = Metadata.from("product_id", idObj.toString());
                        results.add(Content.from(TextSegment.from(contextText, metadata)));
                        cachedIds.add(Integer.valueOf(idObj.toString()));

                        log.info("   ACCEPTED - ID: {} | Score: {} >= minScore {}", idObj, normalizedScore, minScore);
                    } else {
                        log.info("   REJECTED - ID: {} | Score: {} < minScore {}", idObj, normalizedScore, minScore);
                    }
                }

                // Cache kết quả để ChatbotMessageServiceImpl reuse, không cần gọi ES lần 2
                lastRetrievedProductIds.set(cachedIds);

                log.info("[RAG] Returned {} products to LLM", results.size());
                return results;

            } catch (Exception e) {
                log.error("ContentRetriever search failed for query: {}", queryObj.text(), e);
                return List.of();
            }
        };
    }

    private String extractProductName(String contextText) {
        if (contextText == null) return "Unknown";
        int nameStart = contextText.indexOf("Món: ");
        if (nameStart == -1) return "Unknown";
        int nameEnd = contextText.indexOf("\n", nameStart);
        if (nameEnd == -1) nameEnd = Math.min(contextText.length(), nameStart + 50);
        return contextText.substring(nameStart + 5, nameEnd).trim();
    }
}