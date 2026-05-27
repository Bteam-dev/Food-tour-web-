package com.example.FoodTourApp.service;

import java.util.List;
import java.util.Map;

/**
 * Service cho tính năng search kiểu YouTube:
 * - Trending searches (guest)
 * - Search history (logged-in user)
 * - Smart suggestions (hybrid: PhoBERT semantic + autocomplete + trending)
 * - Search analytics logging
 */
public interface SearchSuggestionService {

    // ═══════════════════════════════════════════════════════════════════════════
    // TRENDING (Public - không cần đăng nhập)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Lấy trending searches - hiển thị khi user chưa đăng nhập bấm vào thanh search.
     * Giống YouTube trending searches.
     *
     * @param limit số lượng trending items (default 10)
     * @return List trending items: {queryText, searchCount, isTrending}
     */
    List<Map<String, Object>> getTrendingSearches(int limit);

    // ═══════════════════════════════════════════════════════════════════════════
    // SEARCH HISTORY (User - cần đăng nhập)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Lấy search history của user - hiển thị khi user đã đăng nhập bấm vào thanh search.
     *
     * @param userId user ID
     * @param limit số lượng (default 10)
     * @return List history items: {id, queryText, searchedAt}
     */
    List<Map<String, Object>> getSearchHistory(Integer userId, int limit);

    /**
     * Xóa 1 item search history
     */
    boolean deleteSearchHistoryItem(Integer userId, Long historyId);

    /**
     * Xóa toàn bộ search history của user
     */
    void clearSearchHistory(Integer userId);

    // ═══════════════════════════════════════════════════════════════════════════
    // SMART SUGGESTIONS (Public - khi user đang gõ)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Gợi ý thông minh khi user đang gõ - KẾT HỢP:
     * 1. PhoBERT semantic search trên suggestion index (hiểu tiếng Việt, sai chính tả, không dấu)
     * 2. Autocomplete trên product name (edge_ngram)
     * 3. Trending queries phù hợp
     *
     * Ví dụ: user gõ "pho" → gợi ý "phở bò", "phở gà", "phở cuốn" (từ trending + products)
     * Ví dụ: user gõ "poh" → vẫn gợi ý "phở bò" (PhoBERT hiểu semantic)
     *
     * @param query prefix đang gõ
     * @param userId user ID (nullable - nếu đăng nhập thì ưu tiên history match)
     * @param limit số lượng gợi ý (default 8)
     * @return List suggestions: {text, type(trending/history/product), score}
     */
    List<Map<String, Object>> getSmartSuggestions(String query, Integer userId, int limit);

    // ═══════════════════════════════════════════════════════════════════════════
    // ANALYTICS LOGGING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Log search query (gọi khi user thực hiện search).
     * - Lưu vào search_analytics (cho trending/training)
     * - Lưu vào search_histories (nếu đã đăng nhập)
     */
    void logSearch(Integer userId, String queryText, int resultCount, String sessionId);

    /**
     * Log search click (gọi khi user click vào 1 product từ kết quả search).
     * Dùng cho training PhoBERT model trên Colab.
     */
    void logSearchClick(Integer userId, String queryText, Integer productId, int clickPosition, String sessionId);
}
