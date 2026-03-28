package com.example.FoodTourApp.service.impl;

import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.example.FoodTourApp.service.FoodVectorService;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class FoodVectorServiceImpl implements FoodVectorService {

    private static final Logger log = LoggerFactory.getLogger(FoodVectorServiceImpl.class);

    private EmbeddingModel embeddingModel;
    private ElasticsearchClient esClient;

    @Value("${ollama.base-url}")
    private String ollamaBaseUrl;

    @Value("${ollama.embedding-model}")
    private String embeddingModelName;

    @Value("${elasticsearch.host}")
    private String esHost;

    @Value("${elasticsearch.port}")
    private int esPort;

    @Value("${elasticsearch.index:foodtour_products_chatbot}")
    private String esIndex;

    @Value("${rag.max-results:8}")
    private int maxResults;

    @Value("${rag.min-score:0.55}")
    private double minScore;

    @Value("${app.python-sync-script:scripts/sync_es_chatbot.py}")
    private String pythonSyncScriptPath;

    private ContentRetriever contentRetriever;

    // Small holder to keep Content together with its numeric score so we don't need to call deprecated Metadata.get(String)
    private static class ScoredContent {
        final Content content;
        final double score;

        ScoredContent(Content content, double score) {
            this.content = content;
            this.score = score;
        }
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

            this.contentRetriever = buildContentRetriever();

            log.info("FoodVectorService initialized successfully with index: {}", esIndex);
        } catch (Exception e) {
            log.error("FoodVectorService init failed", e);
        }
    }

    @Override
    public ContentRetriever getContentRetriever() {
        return contentRetriever != null ? contentRetriever : query -> List.of();
    }

    @Override
    public List<Integer> retrieveProductIds(String query) {
        // Implement giống retriever (nếu cần dùng riêng)
        try {
            float[] queryVector = embeddingModel.embed(TextSegment.from(query)).content().vector();
            String vectorJson = Arrays.toString(queryVector);

            String queryBody = String.format("""
                {
                  "size": %d,
                  "query": {
                    "script_score": {
                      "query": { "match_all": {} },
                      "script": {
                        "source": "cosineSimilarity(params.query_vector, 'embedding') + 1.0",
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

            List<Integer> ids = new ArrayList<>();

            List<Hit<Map<String, Object>>> hits = response.hits() == null || response.hits().hits() == null
                    ? List.of()
                    : response.hits().hits();

            for (Hit<Map<String, Object>> hit : hits) {
                Map<String, Object> src = hit.source();
                if (src == null) continue;

                double score = hit.score() != null ? hit.score() - 1.0 : 0.0;
                if (score >= minScore) {
                    Object idObj = src.get("id");
                    if (idObj != null) ids.add(Integer.valueOf(idObj.toString()));
                }
            }
            return ids;
        } catch (Exception e) {
            log.error("retrieveProductIds failed", e);
            return List.of();
        }
    }

    @Override
    public void syncProductToEs(Integer productId) {
        executePython("--product-id", String.valueOf(productId));
    }

    @Override
    public void fullSyncToEs() {
        executePython("--full", null);
    }

    private void executePython(String mode, String param) {
        try {
            List<String> command = new ArrayList<>();
            command.add("python");
            command.add(pythonSyncScriptPath);
            command.add(mode);
            if (param != null) command.add(param);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(Paths.get(".").toAbsolutePath().toFile());
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[Python Sync] {}", line);
                }
            }

            int exitCode = process.waitFor();
            log.info("Python sync {} completed with exit code: {}", mode, exitCode);
        } catch (Exception e) {
            log.error("Failed to execute Python sync script", e);
        }
    }

    private ContentRetriever buildContentRetriever() {
        return queryObj -> {
            try {
                String userQuery = queryObj.text();
                log.info("🔍 [RAG] User query: '{}'", userQuery);

                float[] queryVector = embeddingModel.embed(TextSegment.from(userQuery)).content().vector();
                String vectorJson = Arrays.toString(queryVector);

                String queryBody = String.format("""
                {
                  "size": %d,
                  "query": {
                    "script_score": {
                      "query": { "match_all": {} },
                      "script": {
                        "source": "cosineSimilarity(params.query_vector, 'embedding') + 1.0",
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

                // Use a holder that keeps Content together with its numeric score to avoid using deprecated Metadata.get(String)
                List<ScoredContent> scoredResults = new ArrayList<>();

                List<Hit<Map<String, Object>>> hits = response.hits() == null || response.hits().hits() == null
                        ? List.of()
                        : response.hits().hits();

                log.info("📊 [RAG] Found {} hits from Elasticsearch", hits.size());

                for (Hit<Map<String, Object>> hit : hits) {
                    Map<String, Object> src = hit.source();
                    if (src == null) continue;

                    double rawScore = hit.score() != null ? hit.score() : 0.0;
                    double cosineScore = rawScore - 1.0;

                    String contextText = (String) src.get("context_text");
                    Object idObj = src.get("id");

                    if (contextText != null && idObj != null) {
                        Metadata metadata = Metadata.from("product_id", idObj.toString());

                        // Thêm score vào metadata (dùng put thay vì getDouble với default)
                        metadata.put("score", String.valueOf(cosineScore));   // lưu dưới dạng String vì Metadata hay bị lỗi với Double

                        Content content = Content.from(TextSegment.from(contextText, metadata));

                        scoredResults.add(new ScoredContent(content, cosineScore));

                        log.info("   → ID: {} | Score: {}", idObj, cosineScore);
                    }
                }

                // Sắp xếp theo score giảm dần (ưu tiên món liên quan nhất lên đầu)
                scoredResults.sort((a, b) -> Double.compare(b.score, a.score));

                List<Content> results = scoredResults.stream().map(sc -> sc.content).collect(Collectors.toList());

                log.info("🎯 [RAG] Sorted and returned {} products to LLM (highest score first)", results.size());

                // Log Top 3 món tốt nhất để debug
                for (int i = 0; i < Math.min(4, scoredResults.size()); i++) {
                    ScoredContent sc = scoredResults.get(i);
                    Content content = sc.content;
                    String text = content.textSegment().text();
                    String productName = "Unknown";

                    if (text.contains("Món:")) {
                        int start = text.indexOf("Món:") + 5;
                        int end = text.indexOf("\n", start);
                        if (end == -1) end = text.length();
                        productName = text.substring(start, end).trim();
                    }

                    double score = sc.score;
                    log.info("   Top {}: '{}' | Score: {}", i + 1, productName, score);
                }

                return results;

            } catch (Exception e) {
                log.error("❌ ContentRetriever search failed", e);
                return List.of();
            }
        };
    }
}

