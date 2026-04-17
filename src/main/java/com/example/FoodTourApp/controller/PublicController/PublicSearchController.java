package com.example.FoodTourApp.controller.PublicController;

import com.example.FoodTourApp.service.SearchSuggestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Public Search APIs (không cần đăng nhập).
 *
 * Trending + Smart Suggestions - kiểu YouTube search bar.
 */
@RestController
@RequestMapping("/api/public/search")
public class PublicSearchController {

    private static final Logger log = LoggerFactory.getLogger(PublicSearchController.class);

    private final SearchSuggestionService searchSuggestionService;

    public PublicSearchController(SearchSuggestionService searchSuggestionService) {
        this.searchSuggestionService = searchSuggestionService;
    }

    /**
     * GET /api/public/search/trending?limit=10
     *
     * Lấy trending searches - hiển thị khi user chưa đăng nhập bấm vào thanh search.
     * Giống YouTube: hiện danh sách "xu hướng tìm kiếm".
     *
     * Response: { success: true, data: [{ queryText, searchCount, isTrending }] }
     */
    @GetMapping("/trending")
    public ResponseEntity<?> getTrendingSearches(
            @RequestParam(defaultValue = "10") int limit) {

        log.debug("Get trending searches, limit={}", limit);

        try {
            List<Map<String, Object>> trending = searchSuggestionService.getTrendingSearches(limit);
            return ResponseEntity.ok(Map.of("success", true, "data", trending));
        } catch (Exception e) {
            log.error("Error getting trending: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", true, "data", List.of()));
        }
    }

    /**
     * GET /api/public/search/suggest?q=pho&limit=8
     *
     * Smart suggestions khi user đang gõ (KHÔNG cần đăng nhập).
     * Hybrid: PhoBERT semantic + autocomplete + trending.
     *
     * Ví dụ:
     *   q=pho   → ["phở bò", "phở gà", "phở cuốn"]
     *   q=poh   → ["phở bò", "phở gà"] (PhoBERT hiểu semantic)
     *   q=bun   → ["bún bò", "bún chả", "bún riêu"]
     *
     * Response: { success: true, data: [{ text, type(trending/product/suggestion) }] }
     */
    @GetMapping("/suggest")
    public ResponseEntity<?> getSmartSuggestions(
            @RequestParam("q") String query,
            @RequestParam(defaultValue = "8") int limit) {

        log.debug("Smart suggest: q='{}', limit={}", query, limit);

        try {
            List<Map<String, Object>> suggestions =
                    searchSuggestionService.getSmartSuggestions(query, null, limit);
            return ResponseEntity.ok(Map.of("success", true, "data", suggestions));
        } catch (Exception e) {
            log.error("Error getting suggestions: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", true, "data", List.of()));
        }
    }

    /**
     * POST /api/public/search/log-click
     *
     * Log khi user (guest) click vào product từ kết quả search.
     * Dùng cho training PhoBERT model trên Colab.
     */
    @PostMapping("/log-click")
    public ResponseEntity<?> logSearchClick(@RequestBody Map<String, Object> body) {
        try {
            String queryText = (String) body.get("queryText");
            Integer productId = body.get("productId") != null ?
                    ((Number) body.get("productId")).intValue() : null;
            int clickPosition = body.get("clickPosition") != null ?
                    ((Number) body.get("clickPosition")).intValue() : 0;
            String sessionId = (String) body.get("sessionId");

            searchSuggestionService.logSearchClick(null, queryText, productId, clickPosition, sessionId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("Error logging search click: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("success", true));
        }
    }
}
