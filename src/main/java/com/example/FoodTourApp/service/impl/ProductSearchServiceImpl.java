package com.example.FoodTourApp.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldSort;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.example.FoodTourApp.DTO.ProductDTO.ProductResponseDTO;
import com.example.FoodTourApp.DTO.ProductVariantDTO.VariantResponseDTO;
import com.example.FoodTourApp.entity.ProductVariant;
import com.example.FoodTourApp.repository.ProductVariantRepository;
import com.example.FoodTourApp.repository.ShopRepository;
import com.example.FoodTourApp.service.ProductSearchService;
import jakarta.annotation.PostConstruct;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.concurrent.TimeUnit;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Elasticsearch Hybrid Search Service.
 *
 * SEARCH MODES:
 * 1. BM25 only (khi PhoBERT server offline) - full-text + fuzzy
 * 2. Hybrid BM25 + PhoBERT cosine (khi PhoBERT server online) - semantic understanding
 *
 * PhoBERT chạy trên Google Colab, expose qua Cloudflare tunnel.
 * Hiểu tiếng Việt, sai chính tả, không dấu (pho → phở, poh → phở)
 */
@Service
public class ProductSearchServiceImpl implements ProductSearchService {

    private static final Logger log = LoggerFactory.getLogger(ProductSearchServiceImpl.class);

    private static final String EMB_CACHE_PREFIX = "phobert:emb:";
    private static final int EMB_CACHE_TTL_MINUTES = 30;
    private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private final ProductVariantRepository variantRepository;
    private final ShopRepository shopRepository;
    private final RestTemplate restTemplate;
    private final RedisTemplate<String, Object> redisObjectTemplate;
    private ElasticsearchClient esClient;

    @Value("${elasticsearch.host}")
    private String esHost;

    @Value("${elasticsearch.port}")
    private int esPort;

    @Value("${elasticsearch.search-index}")
    private String searchIndex;

    @Value("${app.python-search-sync-script}")
    private String pythonSyncScript;

    @Value("${phobert.server-url}")
    private String phobertServerUrl;

    @Value("${phobert.enabled}")
    private boolean phobertEnabled;

    @Value("${search.hybrid.bm25-weight}")
    private double bm25Weight;

    @Value("${search.hybrid.semantic-weight}")
    private double semanticWeight;

    public ProductSearchServiceImpl(
            ProductVariantRepository variantRepository,
            ShopRepository shopRepository,
            @Qualifier("redisObjectTemplate") RedisTemplate<String, Object> redisObjectTemplate
    ) {
        this.variantRepository = variantRepository;
        this.shopRepository = shopRepository;
        this.redisObjectTemplate = redisObjectTemplate;
        this.restTemplate = new RestTemplate();
    }

    @PostConstruct
    public void init() {
        try {
            RestClient restClient = RestClient.builder(new HttpHost(esHost, esPort, "http")).build();
            RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
            this.esClient = new ElasticsearchClient(transport);

            log.info("ProductSearchService initialized - index: {}, PhoBERT: {}",
                    searchIndex, phobertEnabled ? phobertServerUrl : "disabled");
        } catch (Exception e) {
            log.error("Failed to initialize ProductSearchService", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SYNC OPERATIONS (Python script)
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    @Async
    public void syncProductToEs(Integer productId) {
        executePythonScript("--product-id", String.valueOf(productId));
    }

    @Override
    @Async
    public void deleteProductFromEs(Integer productId) {
        executePythonScript("--delete-product-id", String.valueOf(productId));
    }

    @Override
    public void fullSyncToEs() {
        executePythonScript("--full", null);
    }

    @Override
    public void recreateIndexAndSync() {
        executePythonScript("--recreate-index", null);
    }

    @Override
    public void updateSalesStatistics() {
        executePythonScript("--update-sales", null);
    }

    @Override
    public void updateShopOpenStatusInEs(Integer shopId, boolean isOpen) {
        try {
            esClient.updateByQuery(u -> u
                    .index(searchIndex)
                    .query(q -> q.term(t -> t.field("shop_id").value(shopId)))
                    .script(s -> s.inline(i -> i
                            .source("ctx._source.shop_is_open = params.isOpen")
                            .params("isOpen", JsonData.of(isOpen))
                    ))
            );
            log.debug("[ShopOpenStatus] shopId={} → isOpen={}", shopId, isOpen);
        } catch (Exception e) {
            log.error("[ShopOpenStatus] Failed to update shopId={}: {}", shopId, e.getMessage());
        }
    }

    private void executePythonScript(String mode, String param) {
        try {
            List<String> command = new ArrayList<>();
            command.add("python");
            command.add(pythonSyncScript);
            command.add(mode);
            if (param != null) {
                command.add(param);
            }

            log.info("[ES Search Sync] Executing: python {} {} {}", pythonSyncScript, mode, param != null ? param : "");

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(Paths.get(".").toAbsolutePath().toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[ES Search Sync] {}", line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                log.info("[ES Search Sync] Completed successfully");
            } else {
                log.warn("[ES Search Sync] Exited with code: {}", exitCode);
            }
        } catch (Exception e) {
            log.error("[ES Search Sync] Failed to execute script", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HYBRID SEARCH (BM25 + PhoBERT cosine)
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public Page<ProductResponseDTO> searchProducts(
            String keyword,
            String city,
            String district,
            Integer categoryId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String sortBy,
            Boolean shopOpen,
            Pageable pageable
    ) {
        checkElasticsearchAvailable();

        try {
            BoolQuery.Builder boolQuery = new BoolQuery.Builder();

            // Base filter: chỉ sản phẩm available
            boolQuery.filter(TermQuery.of(t -> t.field("is_available").value(true))._toQuery());

            // Keyword search
            if (keyword != null && !keyword.isBlank()) {
                String trimmed = keyword.trim();
                Query keywordQuery = buildHybridKeywordQuery(trimmed);
                boolQuery.must(keywordQuery);
            }

            // Filter by city
            if (city != null && !city.isBlank()) {
                boolQuery.filter(TermQuery.of(t -> t
                        .field("shop_city")
                        .value(city.trim().toLowerCase())
                )._toQuery());
            }

            // Filter by district (chỉ áp dụng khi đã chọn city)
            if (district != null && !district.isBlank()) {
                boolQuery.filter(TermQuery.of(t -> t
                        .field("shop_district")
                        .value(district.trim().toLowerCase())
                )._toQuery());
            }

            // Filter by category
            if (categoryId != null) {
                boolQuery.filter(TermQuery.of(t -> t
                        .field("category_id")
                        .value(categoryId)
                )._toQuery());
            }

            // Filter by price range
            if (minPrice != null) {
                boolQuery.filter(RangeQuery.of(r -> r
                        .field("effective_price")
                        .gte(JsonData.of(minPrice.doubleValue()))
                )._toQuery());
            }
            if (maxPrice != null) {
                boolQuery.filter(RangeQuery.of(r -> r
                        .field("effective_price")
                        .lte(JsonData.of(maxPrice.doubleValue()))
                )._toQuery());
            }

            // Build sort: relevance score first when keyword present
            List<SortOptions> sortOptions = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                sortOptions.add(SortOptions.of(so -> so.score(sc -> sc.order(SortOrder.Desc))));
            }
            sortOptions.addAll(buildSortOptions(sortBy));

            // Filter theo trạng thái mở/đóng cửa của shop — tại ES level, pagination chính xác.
            // shop_is_open được cập nhật bởi ShopOpenStatusScheduler theo event-driven:
            // task fire đúng lúc shop mở/đóng cửa, không polling.
            if (shopOpen != null) {
                boolQuery.filter(TermQuery.of(t -> t
                        .field("shop_is_open")
                        .value(shopOpen)
                )._toQuery());
            }

            SearchResponse<Map> response = esClient.search(s -> s
                            .index(searchIndex)
                            .query(boolQuery.build()._toQuery())
                            .sort(sortOptions)
                            .from((int) pageable.getOffset())
                            .size(pageable.getPageSize())
                            .trackTotalHits(t -> t.enabled(true)),
                    Map.class
            );

            List<ProductResponseDTO> results = response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .map(this::mapToProductResponseDTO)
                    .collect(Collectors.toList());

            long totalHits = response.hits().total() != null ? response.hits().total().value() : 0;

            log.debug("Hybrid Search: keyword='{}', city='{}', categoryId={}, shopOpen={}, results={}, total={}",
                    keyword, city, categoryId, shopOpen, results.size(), totalHits);

            return new PageImpl<>(results, pageable, totalHits);

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("ES Search failed", e);
            throw new RuntimeException("Elasticsearch search failed: " + e.getMessage(), e);
        }
    }

    /**
     * Build hybrid keyword query:
     * - Nếu PhoBERT enabled + server online → BM25 + PhoBERT cosine (script_score)
     * - Nếu PhoBERT offline → BM25 only (multi_match + fuzzy)
     */
    private Query buildHybridKeywordQuery(String keyword) {
        // BM25 component: multi-match trên text fields (bỏ shop_name và description)
        Query bm25Query = MultiMatchQuery.of(m -> m
                .query(keyword)
                .fields(
                        "name^3",
                        "name_normalized^3",
                        "ingredients^1",
                        "tags^2",
                        "category_name^1.5",
                        "search_text^1"
                )
                .type(TextQueryType.BestFields)
                .fuzziness("AUTO")
                .prefixLength(1)
                .minimumShouldMatch("70%")
        )._toQuery();

        // Try PhoBERT semantic component
        if (phobertEnabled && phobertServerUrl != null && !phobertServerUrl.isBlank()) {
            List<Double> queryEmbedding = getPhoBertEmbedding(keyword);
            if (queryEmbedding != null && !queryEmbedding.isEmpty()) {
                // Hybrid BM25 + cosine similarity với null-safe script.
                // Doc không có embedding → fallback về BM25 score (tránh script error).
                return FunctionScoreQuery.of(fs -> fs
                        .query(bm25Query)
                        .boostMode(FunctionBoostMode.Replace)
                        .functions(FunctionScore.of(fn -> fn
                                .scriptScore(ScriptScoreFunction.of(ssf -> ssf
                                        .script(sc -> sc.inline(i -> i
                                                .source(
                                                    "double bm25 = _score; " +
                                                    "if (doc['embedding'].size() == 0) { return bm25; } " +
                                                    "double semantic = cosineSimilarity(params.query_vector, 'embedding') + 1.0; " +
                                                    "return params.bm25_w * bm25 + params.sem_w * semantic;"
                                                )
                                                .params("query_vector", JsonData.of(queryEmbedding))
                                                .params("bm25_w", JsonData.of(bm25Weight))
                                                .params("sem_w", JsonData.of(semanticWeight))
                                        ))
                                ))
                        ))
                )._toQuery();
            }
        }

        // Fallback: BM25 only
        return bm25Query;
    }

    /**
     * Gọi PhoBERT server (Colab + Cloudflare tunnel) để lấy query embedding.
     * POST /embed { "text": "phở bò" } → { "embedding": [0.1, -0.3, ...] }
     *
     * Có Redis cache (TTL 30 phút) để tránh gọi PhoBERT lặp lại với cùng query.
     */
    private List<Double> getPhoBertEmbedding(String text) {
        String cacheKey = EMB_CACHE_PREFIX + normalizeVietnamese(text);

        // 1. Check Redis cache
        try {
            @SuppressWarnings("unchecked")
            List<Double> cached = (List<Double>) redisObjectTemplate.opsForValue().get(cacheKey);
            if (cached != null && !cached.isEmpty()) {
                log.debug("PhoBERT embedding cache hit for '{}'", text);
                return cached;
            }
        } catch (Exception e) {
            log.debug("Redis embedding cache miss for '{}'", text);
        }

        // 2. Gọi PhoBERT server
        try {
            Map<String, String> request = Map.of("text", text);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    phobertServerUrl + "/embed", request, Map.class
            );

            if (response != null && response.containsKey("embedding")) {
                @SuppressWarnings("unchecked")
                List<Double> embedding = (List<Double>) response.get("embedding");

                // Cache lại để dùng cho các request giống nhau
                try {
                    redisObjectTemplate.opsForValue().set(cacheKey, embedding,
                            EMB_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
                } catch (Exception ignored) {}

                return embedding;
            }
        } catch (Exception e) {
            log.warn("PhoBERT server unreachable for '{}': {} - falling back to BM25 only",
                    text, e.getMessage());
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // OTHER SEARCH OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public Page<ProductResponseDTO> searchProductsByShop(
            Integer shopId,
            String keyword,
            String sortBy,
            Pageable pageable
    ) {
        checkElasticsearchAvailable();

        try {
            BoolQuery.Builder boolQuery = new BoolQuery.Builder();
            boolQuery.filter(TermQuery.of(t -> t.field("is_available").value(true))._toQuery());
            boolQuery.filter(TermQuery.of(t -> t.field("shop_id").value(shopId))._toQuery());

            if (keyword != null && !keyword.isBlank()) {
                boolQuery.must(MatchQuery.of(m -> m
                        .field("name")
                        .query(keyword.trim())
                        .fuzziness("AUTO")
                )._toQuery());
            }

            List<SortOptions> sortOptions = buildSortOptions(sortBy);

            SearchResponse<Map> response = esClient.search(s -> s
                            .index(searchIndex)
                            .query(boolQuery.build()._toQuery())
                            .sort(sortOptions)
                            .from((int) pageable.getOffset())
                            .size(pageable.getPageSize())
                            .trackTotalHits(t -> t.enabled(true)),
                    Map.class
            );

            List<ProductResponseDTO> results = response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .map(this::mapToProductResponseDTO)
                    .collect(Collectors.toList());

            long totalHits = response.hits().total() != null ? response.hits().total().value() : 0;

            return new PageImpl<>(results, pageable, totalHits);

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("ES Search by shop failed", e);
            throw new RuntimeException("Elasticsearch search failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> suggestProducts(String prefix, int limit) {
        checkElasticsearchAvailable();

        if (prefix == null || prefix.isBlank()) {
            return List.of();
        }

        try {
            // Hybrid suggest: autocomplete + PhoBERT semantic
            BoolQuery.Builder boolQuery = new BoolQuery.Builder();

            // Autocomplete match (edge_ngram)
            boolQuery.should(MatchQuery.of(m -> m
                    .field("name.autocomplete")
                    .query(prefix.trim())
            )._toQuery());

            // Normalized match (handles no-diacritics input)
            boolQuery.should(MatchQuery.of(m -> m
                    .field("name_normalized")
                    .query(prefix.trim())
                    .fuzziness("AUTO")
            )._toQuery());

            // PhoBERT semantic boost (if available)
            if (phobertEnabled) {
                List<Double> emb = getPhoBertEmbedding(prefix.trim());
                if (emb != null && !emb.isEmpty()) {
                    boolQuery.should(ScriptScoreQuery.of(ss -> ss
                            .query(q -> q.matchAll(m -> m))
                            .script(sc -> sc.inline(i -> i
                                    .source("cosineSimilarity(params.query_vector, 'embedding') + 1.0")
                                    .params("query_vector", JsonData.of(emb))
                            ))
                    )._toQuery());
                }
            }

            boolQuery.minimumShouldMatch("1");
            boolQuery.filter(TermQuery.of(t -> t.field("is_available").value(true))._toQuery());

            SearchResponse<Map> response = esClient.search(s -> s
                            .index(searchIndex)
                            .query(boolQuery.build()._toQuery())
                            .size(limit)
                            .source(sc -> sc.filter(f -> f.includes(
                                    "id", "name", "image_urls", "effective_price", "rating"
                            ))),
                    Map.class
            );

            return response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .map(source -> {
                        Map<String, Object> suggestion = new HashMap<>();
                        suggestion.put("id", source.get("id"));
                        suggestion.put("name", source.get("name"));
                        suggestion.put("price", source.get("effective_price"));
                        suggestion.put("rating", source.get("rating"));

                        Object imageUrls = source.get("image_urls");
                        if (imageUrls instanceof List && !((List<?>) imageUrls).isEmpty()) {
                            suggestion.put("imageUrl", ((List<?>) imageUrls).get(0));
                        }

                        return suggestion;
                    })
                    .collect(Collectors.toList());

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("ES Suggest failed", e);
            throw new RuntimeException("Elasticsearch suggest failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<ProductResponseDTO> findSimilarProducts(Integer productId, int limit) {
        checkElasticsearchAvailable();

        try {
            SearchResponse<Map> getResponse = esClient.search(s -> s
                            .index(searchIndex)
                            .query(q -> q.term(t -> t.field("id").value(productId)))
                            .size(1),
                    Map.class
            );

            if (getResponse.hits().hits().isEmpty()) {
                return List.of();
            }

            Map<String, Object> source = getResponse.hits().hits().get(0).source();
            if (source == null) {
                return List.of();
            }

            Integer categoryId = (Integer) source.get("category_id");
            @SuppressWarnings("unchecked")
            List<String> tags = (List<String>) source.get("tags");

            BoolQuery.Builder boolQuery = new BoolQuery.Builder();

            if (categoryId != null) {
                boolQuery.must(TermQuery.of(t -> t.field("category_id").value(categoryId))._toQuery());
            }

            if (tags != null && !tags.isEmpty()) {
                boolQuery.should(TermsQuery.of(t -> t
                        .field("tags")
                        .terms(ts -> ts.value(tags.stream()
                                .map(FieldValue::of)
                                .collect(Collectors.toList())))
                )._toQuery());
            }

            boolQuery.mustNot(TermQuery.of(t -> t.field("id").value(productId))._toQuery());
            boolQuery.filter(TermQuery.of(t -> t.field("is_available").value(true))._toQuery());

            SearchResponse<Map> response = esClient.search(s -> s
                            .index(searchIndex)
                            .query(boolQuery.build()._toQuery())
                            .sort(SortOptions.of(so -> so
                                    .field(FieldSort.of(fs -> fs.field("rating").order(SortOrder.Desc)))))
                            .size(limit),
                    Map.class
            );

            return response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .map(this::mapToProductResponseDTO)
                    .collect(Collectors.toList());

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("ES Find similar failed", e);
            throw new RuntimeException("Elasticsearch find similar failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> getAvailableCities() {
        checkElasticsearchAvailable();

        try {
            SearchResponse<Void> response = esClient.search(s -> s
                            .index(searchIndex)
                            .size(0)
                            .query(q -> q.term(t -> t.field("is_available").value(true)))
                            .aggregations("cities", Aggregation.of(a -> a
                                    .terms(t -> t.field("shop_city").size(100))
                            )),
                    Void.class
            );

            return response.aggregations().get("cities").sterms().buckets().array().stream()
                    .map(StringTermsBucket::key)
                    .map(k -> k._get().toString())
                    .collect(Collectors.toList());

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("ES Get cities failed", e);
            throw new RuntimeException("Elasticsearch get cities failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> getAvailableDistricts(String city) {
        checkElasticsearchAvailable();

        try {
            BoolQuery.Builder boolQuery = new BoolQuery.Builder();
            boolQuery.filter(TermQuery.of(t -> t.field("is_available").value(true))._toQuery());

            // Chỉ lấy quận trong thành phố đã chọn
            if (city != null && !city.isBlank()) {
                boolQuery.filter(TermQuery.of(t -> t
                        .field("shop_city")
                        .value(city.trim().toLowerCase())
                )._toQuery());
            }

            SearchResponse<Void> response = esClient.search(s -> s
                            .index(searchIndex)
                            .size(0)
                            .query(boolQuery.build()._toQuery())
                            .aggregations("districts", Aggregation.of(a -> a
                                    .terms(t -> t.field("shop_district").size(100))
                            )),
                    Void.class
            );

            return response.aggregations().get("districts").sterms().buckets().array().stream()
                    .map(StringTermsBucket::key)
                    .map(k -> k._get().toString())
                    .filter(d -> !d.isBlank())
                    .sorted()
                    .collect(Collectors.toList());

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("ES Get districts failed for city={}", city, e);
            throw new RuntimeException("Elasticsearch get districts failed: " + e.getMessage(), e);
        }
    }

    @Override
    public PriceRangeStats getPriceRangeStats(String city, Integer categoryId) {
        checkElasticsearchAvailable();

        try {
            BoolQuery.Builder boolQuery = new BoolQuery.Builder();
            boolQuery.filter(TermQuery.of(t -> t.field("is_available").value(true))._toQuery());

            if (city != null && !city.isBlank()) {
                boolQuery.filter(TermQuery.of(t -> t.field("shop_city").value(city.toLowerCase()))._toQuery());
            }
            if (categoryId != null) {
                boolQuery.filter(TermQuery.of(t -> t.field("category_id").value(categoryId))._toQuery());
            }

            SearchResponse<Void> response = esClient.search(s -> s
                            .index(searchIndex)
                            .size(0)
                            .query(boolQuery.build()._toQuery())
                            .aggregations("price_stats", Aggregation.of(a -> a
                                    .stats(st -> st.field("effective_price"))
                            )),
                    Void.class
            );

            var stats = response.aggregations().get("price_stats").stats();

            return new PriceRangeStats(
                    BigDecimal.valueOf(stats.min()),
                    BigDecimal.valueOf(stats.max()),
                    BigDecimal.valueOf(stats.avg())
            );

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("ES Get price stats failed", e);
            throw new RuntimeException("Elasticsearch get price stats failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isElasticsearchAvailable() {
        try {
            return esClient != null && esClient.ping().value();
        } catch (Exception e) {
            log.warn("Elasticsearch is not available: {}", e.getMessage());
            return false;
        }
    }

    private void checkElasticsearchAvailable() {
        if (!isElasticsearchAvailable()) {
            throw new RuntimeException("Elasticsearch is not available. Please ensure Elasticsearch is running on " + esHost + ":" + esPort);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private String normalizeVietnamese(String text) {
        if (text == null) return "";
        text = text.replace("Đ", "D").replace("đ", "d");
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(normalized).replaceAll("").toLowerCase();
    }

    private List<SortOptions> buildSortOptions(String sortBy) {
        List<SortOptions> sortOptions = new ArrayList<>();

        if (sortBy == null || sortBy.isBlank()) {
            sortBy = "newest";
        }

        switch (sortBy.toLowerCase()) {
            case "rating_desc":
                sortOptions.add(SortOptions.of(so -> so
                        .field(FieldSort.of(fs -> fs.field("rating_score").order(SortOrder.Desc)))));
                break;
            case "rating_asc":
                sortOptions.add(SortOptions.of(so -> so
                        .field(FieldSort.of(fs -> fs.field("rating").order(SortOrder.Asc)))));
                break;
            case "price_asc":
                sortOptions.add(SortOptions.of(so -> so
                        .field(FieldSort.of(fs -> fs.field("effective_price").order(SortOrder.Asc)))));
                break;
            case "price_desc":
                sortOptions.add(SortOptions.of(so -> so
                        .field(FieldSort.of(fs -> fs.field("effective_price").order(SortOrder.Desc)))));
                break;
            case "best_selling":
                sortOptions.add(SortOptions.of(so -> so
                        .field(FieldSort.of(fs -> fs.field("total_sold").order(SortOrder.Desc)))));
                sortOptions.add(SortOptions.of(so -> so
                        .field(FieldSort.of(fs -> fs.field("rating").order(SortOrder.Desc)))));
                break;
            case "newest":
            default:
                sortOptions.add(SortOptions.of(so -> so
                        .field(FieldSort.of(fs -> fs.field("created_at").order(SortOrder.Desc)))));
                break;
        }

        return sortOptions;
    }

    @SuppressWarnings("unchecked")
    private ProductResponseDTO mapToProductResponseDTO(Map<String, Object> source) {
        ProductResponseDTO dto = new ProductResponseDTO();

        dto.setId(getInteger(source, "id"));
        dto.setShopId(getInteger(source, "shop_id"));
        dto.setShopName(getString(source, "shop_name"));
        // Đọc shop_is_open từ ES (được cập nhật bởi ShopOpenStatusScheduler event-driven).
        // Fallback sang tính runtime nếu field chưa có (index cũ chưa sync lại).
        Boolean shopIsOpen = getBoolean(source, "shop_is_open");
        dto.setShopIsOpen(shopIsOpen != null ? shopIsOpen : isShopCurrentlyOpen(getString(source, "opening_hours")));
        dto.setCategoryId(getInteger(source, "category_id"));
        dto.setCategoryName(getString(source, "category_name"));
        dto.setName(getString(source, "name"));
        dto.setDescription(getString(source, "description"));
        dto.setPrice(getBigDecimal(source, "price"));
        dto.setDiscountPrice(getBigDecimal(source, "discount_price"));
        dto.setIsAvailable(getBoolean(source, "is_available"));
        dto.setPreparationTime(getInteger(source, "preparation_time"));
        dto.setStockQuantity(getInteger(source, "stock_quantity"));
        dto.setMinOrderQuantity(getInteger(source, "min_order_quantity"));
        dto.setMaxOrderQuantity(getInteger(source, "max_order_quantity"));
        dto.setRating(getDouble(source, "rating"));
        dto.setTotalReviews(getLong(source, "total_reviews"));

        dto.setImageUrls(getStringList(source, "image_urls"));
        dto.setIngredients(getStringList(source, "ingredients_list"));
        dto.setNutritionInfo(getStringList(source, "nutrition_info"));
        dto.setTags(getStringList(source, "tags"));

        dto.setCreatedAt(parseDateTime(getString(source, "created_at")));
        dto.setUpdatedAt(parseDateTime(getString(source, "updated_at")));

        if (dto.getId() != null) {
            try {
                List<ProductVariant> variants = variantRepository.findActiveByProductId(dto.getId());
                dto.setVariants(variants.stream()
                        .map(this::mapToVariantResponseDTO)
                        .collect(Collectors.toList()));
            } catch (Exception e) {
                log.warn("Failed to load variants for product {}", dto.getId());
                dto.setVariants(List.of());
            }
        }

        return dto;
    }

    private VariantResponseDTO mapToVariantResponseDTO(ProductVariant variant) {
        VariantResponseDTO dto = new VariantResponseDTO();
        dto.setId(variant.getId());
        dto.setVariantTypeId(variant.getVariantType().getId());
        dto.setVariantTypeName(variant.getVariantType().getName());
        dto.setVariantValue(variant.getVariantValue());
        dto.setPriceAdjustment(variant.getPriceAdjustment());
        dto.setIsActive(variant.getIsActive());
        return dto;
    }

    private Integer getInteger(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        return null;
    }

    private Long getLong(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value == null) return null;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Number) return ((Number) value).longValue();
        return null;
    }

    private Double getDouble(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value == null) return null;
        if (value instanceof Double) return (Double) value;
        if (value instanceof Number) return ((Number) value).doubleValue();
        return null;
    }

    private BigDecimal getBigDecimal(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value == null) return null;
        if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
        return null;
    }

    private Boolean getBoolean(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value == null) return null;
        if (value instanceof Boolean) return (Boolean) value;
        return null;
    }

    private String getString(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value == null) return null;
        return value.toString();
    }

    @SuppressWarnings("unchecked")
    private List<String> getStringList(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value == null) return null;
        if (value instanceof List) {
            return ((List<?>) value).stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
        }
        return null;
    }

    private LocalDateTime parseDateTime(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Tính trạng thái mở/đóng cửa của shop dựa trên opening_hours JSON từ ES.
     * Format: {"monday":{"open":"08:00","close":"22:00"}, ...}
     * Nếu opening_hours null/rỗng → coi như mở cửa 24/7.
     */
    @SuppressWarnings("unchecked")
    private boolean isShopCurrentlyOpen(String openingHoursJson) {
        if (openingHoursJson == null || openingHoursJson.isBlank()) return true;
        try {
            Map<String, Map<String, String>> hoursMap = OBJECT_MAPPER.readValue(openingHoursJson,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Map<String, String>>>() {});

            LocalDateTime now = LocalDateTime.now();
            String dayKey = now.getDayOfWeek().name().toLowerCase();
            Map<String, String> todayHours = hoursMap.get(dayKey);
            if (todayHours == null) return false;

            String openStr = todayHours.get("open");
            String closeStr = todayHours.get("close");
            if (openStr == null || closeStr == null) return false;

            LocalTime currentTime = now.toLocalTime();
            LocalTime openTime = LocalTime.parse(openStr);
            LocalTime closeTime = LocalTime.parse(closeStr);

            if (closeTime.isBefore(openTime)) {
                // Overnight hours (e.g. 22:00–02:00)
                return currentTime.isAfter(openTime) || currentTime.isBefore(closeTime);
            }
            return !currentTime.isBefore(openTime) && currentTime.isBefore(closeTime);
        } catch (Exception e) {
            log.warn("Failed to parse opening_hours: {}", e.getMessage());
            return true;
        }
    }
}
