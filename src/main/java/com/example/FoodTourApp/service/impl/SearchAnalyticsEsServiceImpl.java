package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.service.SearchAnalyticsEsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lưu SearchAnalytics vào Elasticsearch index "search_analytics".
 *
 * Index này dùng time-series pattern: ghi append-only, query aggregation.
 * Không cần UPDATE hay DELETE từng document → ES là lựa chọn tối ưu.
 *
 * Retention: @Scheduled xóa documents cũ hơn 90 ngày mỗi đêm
 * (thay vì để table MySQL phình ra vô hạn).
 */
@Service
@Slf4j
public class SearchAnalyticsEsServiceImpl implements SearchAnalyticsEsService {

    @Value("${elasticsearch.analytics-index}")
    private String INDEX;
    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ISO_DATE_TIME;
    private static final int RETENTION_DAYS = 90;
    private static final long DEDUP_WINDOW_MS = 10_000L;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Long> searchDedupCache = new ConcurrentHashMap<>();

    @Value("${elasticsearch.host}")
    private String esHost;

    @Value("${elasticsearch.port}")
    private int esPort;

    private String esUrl;

    public SearchAnalyticsEsServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        esUrl = "http://" + esHost + ":" + esPort;
        createIndexIfNotExists();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // INDEX SETUP
    // ══════════════════════════════════════════════════════════════════════════

    private void createIndexIfNotExists() {
        try {
            ResponseEntity<Void> head = restTemplate.exchange(
                    esUrl + "/" + INDEX, HttpMethod.HEAD, null, Void.class);
            if (head.getStatusCode().is2xxSuccessful()) {
                log.info("✅ ES index '{}' already exists", INDEX);
                return;
            }
        } catch (Exception e) {
            // 404 = index chưa tồn tại → tạo mới
        }

        try {
            Map<String, Object> mapping = Map.of(
                "mappings", Map.of(
                    "properties", Map.of(
                        "user_id",            Map.of("type", "integer"),
                        "query_text",         Map.of("type", "keyword"),
                        "query_normalized",   Map.of("type", "keyword"),
                        "result_count",       Map.of("type", "integer"),
                        "clicked_product_id", Map.of("type", "integer"),
                        "click_position",     Map.of("type", "integer"),
                        "session_id",         Map.of("type", "keyword"),
                        "searched_at",        Map.of("type", "date")
                    )
                ),
                "settings", Map.of(
                    "number_of_shards",   1,
                    "number_of_replicas", 0  // single-node setup
                )
            );
            restTemplate.put(esUrl + "/" + INDEX, mapping);
            log.info("✅ Created ES index '{}'", INDEX);
        } catch (Exception e) {
            log.warn("⚠️ Failed to create ES index '{}': {}", INDEX, e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // WRITE - async, non-blocking
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    @Async
    public void logSearch(Integer userId, String queryText, String queryNormalized,
                          int resultCount, String sessionId) {
        try {
            // Dedup: cùng session + query trong 60 giây → bỏ qua, tránh tạo bản ghi thừa
            // do loadProducts() bị gọi nhiều lần (token load, screen resume, pagination...).
            if (sessionId != null && !sessionId.isBlank()) {
                String dedupKey = sessionId + ":" + queryNormalized;
                long now = System.currentTimeMillis();
                Long last = searchDedupCache.get(dedupKey);
                if (last != null && now - last < DEDUP_WINDOW_MS) {
                    log.debug("⏭ Dedup search: session={}, query={}", sessionId, queryNormalized);
                    return;
                }
                searchDedupCache.put(dedupKey, now);
                // Giữ cache nhỏ gọn
                if (searchDedupCache.size() > 5000) searchDedupCache.clear();
            }

            Map<String, Object> doc = new HashMap<>();
            doc.put("user_id",          userId);
            doc.put("query_text",        queryText);
            doc.put("query_normalized",  queryNormalized);
            doc.put("result_count",      resultCount);
            doc.put("session_id",        sessionId);
            doc.put("searched_at",       LocalDateTime.now().format(ISO_FMT));
            restTemplate.postForEntity(esUrl + "/" + INDEX + "/_doc", doc, Map.class);
        } catch (Exception e) {
            log.warn("⚠️ Failed to log search to ES: {}", e.getMessage());
        }
    }

    @Override
    @Async
    public void logClick(Integer userId, String queryText, String queryNormalized,
                         Integer productId, int clickPosition, String sessionId) {
        try {
            Map<String, Object> doc = new HashMap<>();
            doc.put("user_id",            userId);
            doc.put("query_text",          queryText);
            doc.put("query_normalized",    queryNormalized);
            doc.put("clicked_product_id",  productId);
            doc.put("click_position",      clickPosition);
            doc.put("session_id",          sessionId);
            doc.put("searched_at",         LocalDateTime.now().format(ISO_FMT));
            restTemplate.postForEntity(esUrl + "/" + INDEX + "/_doc", doc, Map.class);
        } catch (Exception e) {
            log.warn("⚠️ Failed to log search click to ES: {}", e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // READ - trending & training export
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getTrendingQueries(int days, int limit) {
        try {
            // Terms aggregation: group by query_normalized, lấy query_text mẫu từ top_hits
            Map<String, Object> body = Map.of(
                "size", 0,
                "query", Map.of(
                    "range", Map.of(
                        "searched_at", Map.of("gte", "now-" + days + "d")
                    )
                ),
                "aggs", Map.of(
                    "trending", Map.of(
                        "terms", Map.of(
                            "field", "query_normalized",
                            "size", limit,
                            "order", Map.of("_count", "desc")
                        ),
                        "aggs", Map.of(
                            "text_sample", Map.of(
                                "top_hits", Map.of(
                                    "size", 1,
                                    "_source", List.of("query_text")
                                )
                            )
                        )
                    )
                )
            );

            Map<String, Object> resp = restTemplate.postForObject(
                    esUrl + "/" + INDEX + "/_search", body, Map.class);
            if (resp == null) return List.of();

            Map<String, Object> aggs = (Map<String, Object>) resp.get("aggregations");
            if (aggs == null) return List.of();

            Map<String, Object> trendingAgg = (Map<String, Object>) aggs.get("trending");
            List<Map<String, Object>> buckets = (List<Map<String, Object>>) trendingAgg.get("buckets");
            if (buckets == null) return List.of();

            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> bucket : buckets) {
                long count = ((Number) bucket.get("doc_count")).longValue();
                String queryText = extractQueryText(bucket);

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("queryText",    queryText);
                item.put("searchCount",  count);
                item.put("isTrending",   count >= 5);
                result.add(item);
            }
            return result;

        } catch (Exception e) {
            log.warn("⚠️ Failed to get trending from ES: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getSearchesWithClicks(int limit) {
        try {
            Map<String, Object> body = Map.of(
                "size",  limit,
                "query", Map.of("exists", Map.of("field", "clicked_product_id")),
                "sort",  List.of(Map.of("searched_at", Map.of("order", "desc")))
            );

            Map<String, Object> resp = restTemplate.postForObject(
                    esUrl + "/" + INDEX + "/_search", body, Map.class);
            if (resp == null) return List.of();

            Map<String, Object> hitsWrapper = (Map<String, Object>) resp.get("hits");
            List<Map<String, Object>> hits = (List<Map<String, Object>>) hitsWrapper.get("hits");
            if (hits == null) return List.of();

            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> hit : hits) {
                Map<String, Object> src = (Map<String, Object>) hit.get("_source");
                if (src != null) result.add(src);
            }
            return result;

        } catch (Exception e) {
            log.warn("⚠️ Failed to get searches with clicks from ES: {}", e.getMessage());
            return List.of();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TRAINING DATA EXPORT - ES scroll để lấy bulk data cho Colab
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Export search analytics với click data đã được join.
     *
     * ES lưu 2 loại event riêng biệt (append-only — đúng với event-driven architecture):
     *   - Search event: có result_count, không có clicked_product_id
     *   - Click event:  có clicked_product_id + click_position, không có result_count
     *
     * Export join chúng theo (session_id, query_normalized):
     *   1 search + N clicks → N rows (mỗi click là 1 training sample dương)
     *   1 search + 0 click  → 1 row  (training sample âm — user không hài lòng)
     *
     * Đây là chuẩn implicit feedback cho ML: positive = clicked, negative = searched but not clicked.
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> exportAllAnalytics(int days) {
        try {
            // Fetch toàn bộ documents từ ES (cả search events lẫn click events)
            List<Map<String, Object>> allDocs = scrollAll(days);

            // Tách thành 2 nhóm
            // Search events: deduplicate theo (session_id, query_normalized) — giữ bản ghi sớm nhất
            // (allDocs sorted desc → bản ghi cuối trong list là sớm nhất)
            Map<String, Map<String, Object>> searchByKey = new LinkedHashMap<>();
            // click lookup: (session_id + ":" + query_normalized) → list of click docs
            Map<String, List<Map<String, Object>>> clicksByKey = new LinkedHashMap<>();

            for (Map<String, Object> doc : allDocs) {
                boolean isClickEvent = doc.get("clicked_product_id") != null;
                if (isClickEvent) {
                    String key = clickKey(doc);
                    if (key != null) {
                        clicksByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(doc);
                    }
                } else {
                    // Luôn ghi đè → giữ bản ghi SỚM NHẤT (list sort desc, nên ghi đè liên tục cho đến cuối)
                    String key = clickKey(doc);
                    if (key != null) {
                        searchByKey.put(key, doc);
                    } else {
                        // Không có session/query → giữ nguyên, dùng id làm key
                        searchByKey.put("_" + doc.get("id"), doc);
                    }
                }
            }
            List<Map<String, Object>> searchEvents = new ArrayList<>(searchByKey.values());

            // Join: mỗi search event → merge với click(s) tương ứng
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> search : searchEvents) {
                String key = clickKey(search);
                List<Map<String, Object>> clicks = key != null ? clicksByKey.get(key) : null;

                if (clicks == null || clicks.isEmpty()) {
                    // Không có click → row âm (result_count có, clicked_product_id null)
                    result.add(search);
                } else {
                    // Có click(s) → mỗi click tạo 1 row dương
                    for (Map<String, Object> click : clicks) {
                        Map<String, Object> merged = new LinkedHashMap<>(search);
                        merged.put("clicked_product_id", click.get("clicked_product_id"));
                        merged.put("click_position",     click.get("click_position"));
                        result.add(merged);
                    }
                }
            }

            log.info("📦 Exported {} rows ({} search events, {} clicks) from ES ({} days)",
                    result.size(), searchEvents.size(),
                    allDocs.size() - searchEvents.size(), days);
            return result;

        } catch (Exception e) {
            log.warn("⚠️ Failed to export search analytics from ES: {}", e.getMessage());
            return List.of();
        }
    }

    private String clickKey(Map<String, Object> doc) {
        Object sessionId   = doc.get("session_id");
        Object queryNorm   = doc.get("query_normalized");
        if (sessionId == null || queryNorm == null) return null;
        return sessionId + ":" + queryNorm;
    }

    /** Scroll toàn bộ documents trong khoảng thời gian, trả về list với _id đã gắn vào. */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> scrollAll(int days) {
        List<Map<String, Object>> all = new ArrayList<>();
        Map<String, Object> initBody = Map.of(
            "size", 1000,
            "query", Map.of(
                "range", Map.of("searched_at", Map.of("gte", "now-" + days + "d"))
            ),
            "sort", List.of(Map.of("searched_at", Map.of("order", "desc")))
        );

        Map<String, Object> resp = restTemplate.postForObject(
                esUrl + "/" + INDEX + "/_search?scroll=2m", initBody, Map.class);

        while (resp != null) {
            String scrollId = (String) resp.get("_scroll_id");
            Map<String, Object> hitsWrapper = (Map<String, Object>) resp.get("hits");
            if (hitsWrapper == null) break;

            List<Map<String, Object>> hits = (List<Map<String, Object>>) hitsWrapper.get("hits");
            if (hits == null || hits.isEmpty()) break;

            for (Map<String, Object> hit : hits) {
                Map<String, Object> src = (Map<String, Object>) hit.get("_source");
                if (src != null) {
                    Map<String, Object> doc = new LinkedHashMap<>(src);
                    doc.put("id", hit.get("_id"));
                    all.add(doc);
                }
            }

            if (scrollId == null) break;
            Map<String, Object> scrollBody = Map.of("scroll", "2m", "scroll_id", scrollId);
            resp = restTemplate.postForObject(esUrl + "/_search/scroll", scrollBody, Map.class);
        }
        return all;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DATA RETENTION - xóa documents cũ để tránh ES phình ra vô hạn
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Mỗi ngày 3 giờ sáng: xóa search analytics cũ hơn 90 ngày.
     * Giữ data đủ để tính trending (7-30 ngày) và export training (90 ngày).
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void cleanupOldAnalytics() {
        try {
            Map<String, Object> body = Map.of(
                "query", Map.of(
                    "range", Map.of(
                        "searched_at", Map.of(
                            "lt", "now-" + RETENTION_DAYS + "d"
                        )
                    )
                )
            );

            Map<String, Object> resp = restTemplate.postForObject(
                    esUrl + "/" + INDEX + "/_delete_by_query?conflicts=proceed&wait_for_completion=false",
                    body, Map.class);

            if (resp != null) {
                log.info("🗑️ Scheduled cleanup: deleted old search analytics (>{}d) from ES", RETENTION_DAYS);
            }
        } catch (Exception e) {
            log.warn("⚠️ Failed to cleanup old search analytics: {}", e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HELPER
    // ══════════════════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private String extractQueryText(Map<String, Object> bucket) {
        try {
            Map<String, Object> textSample = (Map<String, Object>) bucket.get("text_sample");
            Map<String, Object> hitsWrapper = (Map<String, Object>) textSample.get("hits");
            List<Map<String, Object>> hits = (List<Map<String, Object>>) hitsWrapper.get("hits");
            if (hits != null && !hits.isEmpty()) {
                Map<String, Object> source = (Map<String, Object>) hits.get(0).get("_source");
                if (source != null) return (String) source.get("query_text");
            }
        } catch (Exception ignored) {}
        return (String) bucket.get("key"); // fallback to normalized key
    }
}
