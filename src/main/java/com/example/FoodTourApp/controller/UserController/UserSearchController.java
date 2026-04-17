package com.example.FoodTourApp.controller.UserController;

import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.repository.UserRepository;
import com.example.FoodTourApp.service.SearchSuggestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * User Search APIs (cần đăng nhập).
 *
 * Search history + Smart suggestions với history matching.
 * Giống YouTube: khi đăng nhập, thanh search hiện lịch sử tìm kiếm.
 */
@RestController
@RequestMapping("/api/user/search")
public class UserSearchController {

    private static final Logger log = LoggerFactory.getLogger(UserSearchController.class);

    private final SearchSuggestionService searchSuggestionService;
    private final UserRepository userRepository;

    public UserSearchController(SearchSuggestionService searchSuggestionService,
                                UserRepository userRepository) {
        this.searchSuggestionService = searchSuggestionService;
        this.userRepository = userRepository;
    }

    /**
     * GET /api/user/search/history?limit=10
     *
     * Lấy search history - hiển thị khi user đã đăng nhập bấm vào thanh search.
     * Giống YouTube: hiện danh sách "tìm kiếm gần đây", có nút xóa.
     *
     * Response: { success: true, data: [{ id, queryText, searchedAt }] }
     */
    @GetMapping("/history")
    public ResponseEntity<?> getSearchHistory(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "10") int limit) {

        Integer userId = getUserId(userDetails);
        if (userId == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "User not found"));
        }

        try {
            List<Map<String, Object>> history = searchSuggestionService.getSearchHistory(userId, limit);
            return ResponseEntity.ok(Map.of("success", true, "data", history));
        } catch (Exception e) {
            log.error("Error getting search history: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", true, "data", List.of()));
        }
    }

    /**
     * DELETE /api/user/search/history/{id}
     *
     * Xóa 1 item search history (nút X bên cạnh mỗi item).
     */
    @DeleteMapping("/history/{id}")
    public ResponseEntity<?> deleteHistoryItem(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {

        Integer userId = getUserId(userDetails);
        if (userId == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "User not found"));
        }

        boolean deleted = searchSuggestionService.deleteSearchHistoryItem(userId, id);
        return ResponseEntity.ok(Map.of("success", deleted));
    }

    /**
     * DELETE /api/user/search/history
     *
     * Xóa toàn bộ search history (nút "Xóa tất cả").
     */
    @DeleteMapping("/history")
    public ResponseEntity<?> clearAllHistory(@AuthenticationPrincipal UserDetails userDetails) {

        Integer userId = getUserId(userDetails);
        if (userId == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "User not found"));
        }

        searchSuggestionService.clearSearchHistory(userId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * GET /api/user/search/suggest?q=pho&limit=8
     *
     * Smart suggestions cho user đã đăng nhập.
     * Giống public suggest nhưng thêm history matching (ưu tiên cao nhất).
     */
    @GetMapping("/suggest")
    public ResponseEntity<?> getSmartSuggestions(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("q") String query,
            @RequestParam(defaultValue = "8") int limit) {

        Integer userId = getUserId(userDetails);

        try {
            List<Map<String, Object>> suggestions =
                    searchSuggestionService.getSmartSuggestions(query, userId, limit);
            return ResponseEntity.ok(Map.of("success", true, "data", suggestions));
        } catch (Exception e) {
            log.error("Error getting suggestions: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", true, "data", List.of()));
        }
    }

    /**
     * POST /api/user/search/log-click
     *
     * Log khi user click vào product từ kết quả search (cho training data).
     */
    @PostMapping("/log-click")
    public ResponseEntity<?> logSearchClick(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, Object> body) {

        Integer userId = getUserId(userDetails);

        try {
            String queryText = (String) body.get("queryText");
            Integer productId = body.get("productId") != null ?
                    ((Number) body.get("productId")).intValue() : null;
            int clickPosition = body.get("clickPosition") != null ?
                    ((Number) body.get("clickPosition")).intValue() : 0;
            String sessionId = (String) body.get("sessionId");

            searchSuggestionService.logSearchClick(userId, queryText, productId, clickPosition, sessionId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("Error logging search click: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("success", true));
        }
    }

    private Integer getUserId(UserDetails userDetails) {
        if (userDetails == null) return null;
        try {
            User user = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
            return user != null ? user.getId() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
