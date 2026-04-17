package com.example.FoodTourApp.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.example.FoodTourApp.entity.SearchAnalytics;
import com.example.FoodTourApp.entity.SearchHistory;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.repository.SearchAnalyticsRepository;
import com.example.FoodTourApp.repository.SearchHistoryRepository;
import com.example.FoodTourApp.repository.UserRepository;
import com.example.FoodTourApp.service.SearchSuggestionService;
import jakarta.annotation.PostConstruct;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SearchSuggestionServiceImpl implements SearchSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(SearchSuggestionServiceImpl.class);

    private static final String TRENDING_CACHE_KEY = "search:trending";
    private static final int TRENDING_CACHE_TTL_MINUTES = 10;
    private static final int MAX_HISTORY_PER_USER = 50;
    private static final String EMB_CACHE_PREFIX = "phobert:emb:";
    private static final int EMB_CACHE_TTL_MINUTES = 30;

    private final SearchHistoryRepository searchHistoryRepository;
    private final SearchAnalyticsRepository searchAnalyticsRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisObjectTemplate;
    private final RestTemplate restTemplate;

    private ElasticsearchClient esClient;

    @Value("${elasticsearch.host:localhost}")
    private String esHost;

    @Value("${elasticsearch.port:9200}")
    private int esPort;

    @Value("${elasticsearch.search-index:foodtour_products_search}")
    private String searchIndex;

    @Value("${elasticsearch.suggestion-index:foodtour_search_suggestions}")
    private String suggestionIndex;

    @Value("${phobert.server-url:}")
    private String phobertServerUrl;

    @Value("${phobert.enabled:false}")
    private boolean phobertEnabled;

    public SearchSuggestionServiceImpl(
            SearchHistoryRepository searchHistoryRepository,
            SearchAnalyticsRepository searchAnalyticsRepository,
            UserRepository userRepository,
            @Qualifier("redisObjectTemplate") RedisTemplate<String, Object> redisObjectTemplate
    ) {
        this.searchHistoryRepository = searchHistoryRepository;
        this.searchAnalyticsRepository = searchAnalyticsRepository;
        this.userRepository = userRepository;
        this.redisObjectTemplate = redisObjectTemplate;
        this.restTemplate = new RestTemplate();
    }

    @PostConstruct
    public void init() {
        try {
            RestClient restClient = RestClient.builder(new HttpHost(esHost, esPort, "http")).build();
            RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
            this.esClient = new ElasticsearchClient(transport);
            log.info("SearchSuggestionService initialized - suggestion index: {}, PhoBERT: {}",
                    suggestionIndex, phobertEnabled ? phobertServerUrl : "disabled");
        } catch (Exception e) {
            log.error("Failed to initialize SearchSuggestionService", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TRENDING
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public List<Map<String, Object>> getTrendingSearches(int limit) {
        // 1. Check Redis cache
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cached = (List<Map<String, Object>>) redisObjectTemplate.opsForValue()
                    .get(TRENDING_CACHE_KEY);
            if (cached != null && !cached.isEmpty()) {
                return cached.stream().limit(limit).collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("Redis cache miss for trending: {}", e.getMessage());
        }

        // 2. Query ES suggestion index (populated by Colab)
        List<Map<String, Object>> trending = queryTrendingFromEs(limit);

        // 3. Fallback: tính trending từ MySQL nếu ES không có data
        if (trending.isEmpty()) {
            trending = computeTrendingFromDb(limit);
        }

        // 4. Cache in Redis
        try {
            redisObjectTemplate.opsForValue().set(TRENDING_CACHE_KEY, trending,
                    TRENDING_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Failed to cache trending: {}", e.getMessage());
        }

        return trending;
    }

    private List<Map<String, Object>> queryTrendingFromEs(int limit) {
        try {
            if (esClient == null || !esClient.ping().value()) return List.of();
            if (!esClient.indices().exists(e -> e.index(suggestionIndex)).value()) return List.of();

            SearchResponse<Map> response = esClient.search(s -> s
                            .index(suggestionIndex)
                            .query(q -> q.matchAll(m -> m))
                            .sort(SortOptions.of(so -> so.field(f -> f
                                    .field("es_weight").order(SortOrder.Desc))))
                            .size(limit)
                            .source(sc -> sc.filter(f -> f.includes(
                                    "query_text", "total_searches", "is_trending", "es_weight"
                            ))),
                    Map.class
            );

            return response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .map(src -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("queryText", src.get("query_text"));
                        item.put("searchCount", src.get("total_searches"));
                        item.put("isTrending", Boolean.TRUE.equals(src.get("is_trending")));
                        return item;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to query trending from ES: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Map<String, Object>> computeTrendingFromDb(int limit) {
        try {
            LocalDateTime since = LocalDateTime.now().minusDays(7);
            List<Object[]> rows = searchAnalyticsRepository.findTrendingQueries(since);

            return rows.stream()
                    .limit(limit)
                    .map(row -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("queryText", row[0]);
                        item.put("searchCount", ((Number) row[1]).longValue());
                        item.put("isTrending", ((Number) row[1]).longValue() >= 5);
                        return item;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to compute trending from DB: {}", e.getMessage());
            return List.of();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SEARCH HISTORY
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public List<Map<String, Object>> getSearchHistory(Integer userId, int limit) {
        List<SearchHistory> histories = searchHistoryRepository
                .findByUserIdOrderBySearchedAtDesc(userId);

        return histories.stream()
                .limit(limit)
                .map(h -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", h.getId());
                    item.put("queryText", h.getQueryText());
                    item.put("searchedAt", h.getSearchedAt().format(DateTimeFormatter.ISO_DATE_TIME));
                    return item;
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public boolean deleteSearchHistoryItem(Integer userId, Long historyId) {
        return searchHistoryRepository.deleteByIdAndUserId(historyId, userId) > 0;
    }

    @Override
    @Transactional
    public void clearSearchHistory(Integer userId) {
        searchHistoryRepository.deleteAllByUserId(userId);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SMART SUGGESTIONS (Hybrid: PhoBERT + Autocomplete + Trending)
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public List<Map<String, Object>> getSmartSuggestions(String query, Integer userId, int limit) {
        if (query == null || query.trim().length() < 1) {
            return List.of();
        }

        String trimmed = query.trim();
        String normalized = normalizeVietnamese(trimmed);

        List<Map<String, Object>> results = new ArrayList<>();

        // 1. History match (nếu đã đăng nhập) - ưu tiên cao nhất
        if (userId != null) {
            results.addAll(matchHistorySuggestions(userId, normalized, trimmed, 3));
        }

        // 2. PhoBERT semantic search trên suggestion index (trending + all queries)
        //    Đây là phần KHỦNG: hiểu tiếng Việt, sai chính tả, không dấu
        results.addAll(semanticSuggestFromEs(trimmed, normalized, limit));

        // 3. Product autocomplete (edge_ngram trên product name)
        results.addAll(productAutocompleteSuggestions(trimmed, limit));

        // Deduplicate by normalized text, giữ thứ tự ưu tiên
        Set<String> seen = new LinkedHashSet<>();
        List<Map<String, Object>> deduped = new ArrayList<>();
        for (Map<String, Object> item : results) {
            String key = normalizeVietnamese(String.valueOf(item.get("text")));
            if (seen.add(key)) {
                deduped.add(item);
            }
        }

        return deduped.stream().limit(limit).collect(Collectors.toList());
    }

    /**
     * Match từ search history của user
     */
    private List<Map<String, Object>> matchHistorySuggestions(Integer userId, String normalized, String raw, int limit) {
        try {
            List<SearchHistory> histories = searchHistoryRepository
                    .findByUserIdOrderBySearchedAtDesc(userId);

            return histories.stream()
                    .filter(h -> {
                        String hNorm = h.getQueryNormalized();
                        return hNorm.contains(normalized) || normalized.contains(hNorm);
                    })
                    .limit(limit)
                    .map(h -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("text", h.getQueryText());
                        item.put("type", "history");
                        item.put("historyId", h.getId());
                        return item;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to match history suggestions: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * PhoBERT semantic search trên ES suggestion index.
     * Kết hợp:
     * - BM25 text match trên query_text, no_accent (cho exact/prefix match)
     * - PhoBERT cosine similarity (cho semantic: hiểu "poh" = "phở", sai chính tả)
     */
    private List<Map<String, Object>> semanticSuggestFromEs(String rawQuery, String normalized, int limit) {
        try {
            if (esClient == null || !esClient.ping().value()) return List.of();
            if (!esClient.indices().exists(e -> e.index(suggestionIndex)).value()) return List.of();

            // Build hybrid query
            BoolQuery.Builder boolQuery = new BoolQuery.Builder();

            // BM25 match trên text fields (autocomplete + no_accent)
            boolQuery.should(MultiMatchQuery.of(m -> m
                    .query(rawQuery)
                    .fields("query_text^3", "query_text.autocomplete^2",
                            "no_accent^2", "no_accent.autocomplete^1.5",
                            "query_normalized^1")
                    .type(TextQueryType.BestFields)
                    .fuzziness("AUTO")
                    .prefixLength(1)
            )._toQuery());

            // Nếu PhoBERT enabled → thêm semantic similarity
            if (phobertEnabled && phobertServerUrl != null && !phobertServerUrl.isBlank()) {
                List<Double> queryEmbedding = getPhoBertEmbedding(rawQuery);
                if (queryEmbedding != null && !queryEmbedding.isEmpty()) {
                    // Script score cho cosine similarity
                    boolQuery.should(ScriptScoreQuery.of(ss -> ss
                            .query(q -> q.matchAll(m -> m))
                            .script(sc -> sc.inline(i -> i
                                    .source("cosineSimilarity(params.query_vector, 'embedding') + 1.0")
                                    .params("query_vector", JsonData.of(queryEmbedding))
                            ))
                    )._toQuery());
                }
            }

            boolQuery.minimumShouldMatch("1");

            SearchResponse<Map> response = esClient.search(s -> s
                            .index(suggestionIndex)
                            .query(boolQuery.build()._toQuery())
                            .size(limit)
                            .source(sc -> sc.filter(f -> f.includes(
                                    "query_text", "is_trending", "total_searches", "es_weight"
                            ))),
                    Map.class
            );

            return response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .map(src -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("text", src.get("query_text"));
                        item.put("type", Boolean.TRUE.equals(src.get("is_trending")) ? "trending" : "suggestion");
                        item.put("searchCount", src.get("total_searches"));
                        return item;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed semantic suggest from ES: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Product name autocomplete (edge_ngram) - fallback/bổ sung
     */
    private List<Map<String, Object>> productAutocompleteSuggestions(String query, int limit) {
        try {
            if (esClient == null) return List.of();

            SearchResponse<Map> response = esClient.search(s -> s
                            .index(searchIndex)
                            .query(q -> q.bool(b -> b
                                    .must(MatchQuery.of(m -> m
                                            .field("name.autocomplete")
                                            .query(query)
                                    )._toQuery())
                                    .filter(TermQuery.of(t -> t
                                            .field("is_available").value(true)
                                    )._toQuery())
                            ))
                            .size(limit)
                            .source(sc -> sc.filter(f -> f.includes("name"))),
                    Map.class
            );

            // Trả về product name như 1 suggestion
            Set<String> seen = new HashSet<>();
            return response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .map(src -> String.valueOf(src.get("name")))
                    .filter(name -> seen.add(normalizeVietnamese(name)))
                    .limit(limit)
                    .map(name -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("text", name);
                        item.put("type", "product");
                        return item;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed product autocomplete: {}", e.getMessage());
            return List.of();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ANALYTICS LOGGING
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    @Async
    @Transactional
    public void logSearch(Integer userId, String queryText, int resultCount, String sessionId) {
        if (queryText == null || queryText.isBlank()) return;

        String normalized = normalizeVietnamese(queryText.trim());

        // 1. Lưu vào search_analytics (mọi lượt search)
        SearchAnalytics analytics = SearchAnalytics.builder()
                .userId(userId)
                .queryText(queryText.trim())
                .queryNormalized(normalized)
                .resultCount(resultCount)
                .sessionId(sessionId)
                .build();
        searchAnalyticsRepository.save(analytics);

        // 2. Lưu vào search_histories (nếu đã đăng nhập)
        if (userId != null) {
            saveToHistory(userId, queryText.trim(), normalized, resultCount);
        }

        // 3. Invalidate trending cache (vì có search mới)
        try {
            redisObjectTemplate.delete(TRENDING_CACHE_KEY);
        } catch (Exception ignored) {}
    }

    @Override
    @Async
    @Transactional
    public void logSearchClick(Integer userId, String queryText, Integer productId, int clickPosition, String sessionId) {
        if (queryText == null || queryText.isBlank() || productId == null) return;

        String normalized = normalizeVietnamese(queryText.trim());

        SearchAnalytics analytics = SearchAnalytics.builder()
                .userId(userId)
                .queryText(queryText.trim())
                .queryNormalized(normalized)
                .clickedProductId(productId)
                .clickPosition(clickPosition)
                .sessionId(sessionId)
                .build();
        searchAnalyticsRepository.save(analytics);
    }

    /**
     * Lưu vào history, deduplicate theo normalized query.
     * Nếu đã có thì update timestamp (đẩy lên đầu).
     * Giữ tối đa MAX_HISTORY_PER_USER items.
     */
    private void saveToHistory(Integer userId, String queryText, String normalized, int resultCount) {
        try {
            Optional<SearchHistory> existing = searchHistoryRepository
                    .findByUserIdAndQueryNormalized(userId, normalized);

            if (existing.isPresent()) {
                // Update timestamp để đẩy lên đầu
                SearchHistory history = existing.get();
                history.setQueryText(queryText);
                history.setResultCount(resultCount);
                history.setSearchedAt(LocalDateTime.now());
                searchHistoryRepository.save(history);
            } else {
                // Insert mới
                User user = userRepository.findById(userId).orElse(null);
                if (user == null) return;

                SearchHistory history = SearchHistory.builder()
                        .user(user)
                        .queryText(queryText)
                        .queryNormalized(normalized)
                        .resultCount(resultCount)
                        .build();
                searchHistoryRepository.save(history);

                // Trim nếu vượt quá limit
                long count = searchHistoryRepository.countByUserId(userId);
                if (count > MAX_HISTORY_PER_USER) {
                    List<SearchHistory> all = searchHistoryRepository
                            .findByUserIdOrderBySearchedAtDesc(userId);
                    List<SearchHistory> toDelete = all.subList(MAX_HISTORY_PER_USER, all.size());
                    searchHistoryRepository.deleteAll(toDelete);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to save search history for user {}: {}", userId, e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PhoBERT EMBEDDING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Gọi PhoBERT server (Colab + Cloudflare tunnel) để lấy embedding.
     * Server expose POST /embed { "text": "..." } → { "embedding": [...] }
     *
     * Có Redis cache (TTL 30 phút) - dùng chung key với ProductSearchServiceImpl.
     */
    private List<Double> getPhoBertEmbedding(String text) {
        if (!phobertEnabled || phobertServerUrl == null || phobertServerUrl.isBlank()) {
            return null;
        }

        String cacheKey = EMB_CACHE_PREFIX + normalizeVietnamese(text);

        // 1. Check Redis cache
        try {
            @SuppressWarnings("unchecked")
            List<Double> cached = (List<Double>) redisObjectTemplate.opsForValue().get(cacheKey);
            if (cached != null && !cached.isEmpty()) {
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

                try {
                    redisObjectTemplate.opsForValue().set(cacheKey, embedding,
                            EMB_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
                } catch (Exception ignored) {}

                return embedding;
            }
        } catch (Exception e) {
            log.warn("PhoBERT embedding failed for '{}': {}", text, e.getMessage());
        }
        return null;
    }


    // ═══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private String normalizeVietnamese(String text) {
        if (text == null) return "";
        text = text.replace("Đ", "D").replace("đ", "d");
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(normalized).replaceAll("").toLowerCase().trim();
    }
}
