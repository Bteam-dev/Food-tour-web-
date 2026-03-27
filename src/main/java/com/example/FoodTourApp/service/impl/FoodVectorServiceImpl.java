package com.example.FoodTourApp.service.impl;

import co.elastic.clients.transport.rest_client.RestClientTransport;
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
            for (Hit<Map<String, Object>> hit : response.hits().hits()) {
                double score = hit.score() != null ? hit.score() - 1.0 : 0.0;
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

                List<Content> results = new ArrayList<>();

                log.info("📊 [RAG] Found {} hits from Elasticsearch", response.hits().hits().size());

                for (Hit<Map<String, Object>> hit : response.hits().hits()) {
                    double rawScore = hit.score() != null ? hit.score() : 0.0;
                    double cosineScore = rawScore - 1.0;   // vì ta cộng +1.0 trong script

                    String contextText = (String) hit.source().get("context_text");
                    Object idObj = hit.source().get("id");
                    String productName = "Unknown";

                    // Trích xuất tên món từ context_text để log dễ nhìn
                    if (contextText != null) {
                        int nameStart = contextText.indexOf("Món: ");
                        if (nameStart != -1) {
                            int nameEnd = contextText.indexOf("\n", nameStart);
                            if (nameEnd == -1) nameEnd = contextText.indexOf("Quán:", nameStart);
                            productName = contextText.substring(nameStart + 5, nameEnd != -1 ? nameEnd : nameStart + 50).trim();
                        }
                    }

                    log.info("   → Product ID: {} | Name: '{}' | Score: {:.4f} (raw: {:.4f})",
                            idObj, productName, cosineScore, rawScore);

                    if (cosineScore >= minScore && contextText != null && idObj != null) {
                        Metadata metadata = Metadata.from("product_id", idObj.toString());
                        results.add(Content.from(TextSegment.from(contextText, metadata)));

                        log.info("   ✅ ACCEPTED - ID: {} | Score: {:.4f} >= minScore {}",
                                idObj, cosineScore, minScore);
                    } else {
                        log.info("   ❌ REJECTED - ID: {} | Score: {:.4f} < minScore {}",
                                idObj, cosineScore, minScore);
                    }
                }

                log.info("🎯 [RAG] Finally returned {} products to LLM (after filtering minScore)", results.size());

                return results;

            } catch (Exception e) {
                log.error("❌ ContentRetriever search failed for query: {}", queryObj.text(), e);
                return List.of();
            }
        };
    }
}