package com.example.FoodTourApp.service;

import java.util.List;
import java.util.Map;

/**
 * Lưu SearchAnalytics vào Elasticsearch thay vì MySQL.
 *
 * Lý do dùng ES thay MySQL:
 * - SearchAnalytics là pure analytics (append-only, không cần ACID transaction)
 * - Volume rất lớn (vài triệu records/ngày) → MySQL sẽ bị bottleneck
 * - ES index tối ưu cho time-series data và aggregation (trending queries)
 * - Đã có ES sẵn trong project → không tốn thêm chi phí
 *
 * Training data: SearchAnalytics không dùng trực tiếp để train recommendation model
 * (UserBehavior mới là nguồn chính). Nếu cần export clicks, dùng getSearchesWithClicks().
 */
public interface SearchAnalyticsEsService {

    /**
     * Log 1 lượt tìm kiếm vào ES (async, non-blocking).
     */
    void logSearch(Integer userId, String queryText, String queryNormalized,
                   int resultCount, String sessionId);

    /**
     * Log lượt click vào kết quả tìm kiếm (async, non-blocking).
     */
    void logClick(Integer userId, String queryText, String queryNormalized,
                  Integer productId, int clickPosition, String sessionId);

    /**
     * Lấy trending searches dựa trên ES terms aggregation (real-time).
     *
     * @param days  khoảng thời gian (ngày)
     * @param limit số lượng trending queries tối đa
     */
    List<Map<String, Object>> getTrendingQueries(int days, int limit);

    /**
     * Lấy các search có click (dùng cho training data export nếu cần).
     */
    List<Map<String, Object>> getSearchesWithClicks(int limit);

    /**
     * Bulk export toàn bộ analytics trong N ngày gần nhất dùng ES scroll API.
     * Dùng cho Admin API export search_analytics.csv → upload Colab train PhoBERT.
     *
     * @param days khoảng thời gian xuất (thường 90 ngày)
     * @return list tất cả records, mỗi record là Map với đầy đủ fields
     */
    List<Map<String, Object>> exportAllAnalytics(int days);

    /**
     * Scheduled job: Xóa search analytics cũ hơn 90 ngày mỗi đêm.
     * Giữ data đủ để tính trending và export training mà không để ES phình ra vô hạn.
     */
    void cleanupOldAnalytics();
}
