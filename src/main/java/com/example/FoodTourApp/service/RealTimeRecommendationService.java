package com.example.FoodTourApp.service;

import com.example.FoodTourApp.DTO.ProductDTO.ProductResponseDTO;
import com.example.FoodTourApp.entity.UserBehavior;

import java.util.List;

/**
 * Service xử lý recommendation real-time như YouTube
 * 
 * Kiến trúc:
 * 1. Track mọi hành vi user vào DB + Redis cache
 * 2. Khi gọi getRecommendations:
 *    - Lấy user embedding từ Redis (hoặc tính online từ recent behaviors)
 *    - Query ES KNN để tìm products gần nhất
 *    - Apply diversity rules (không quá nhiều từ 1 shop)
 *    - Exclude products đã tương tác
 * 3. Real-time update: mỗi behavior mới → update user embedding cache
 */
public interface RealTimeRecommendationService {

    // ══════════════════════════════════════════════════════════════════════════
    // BEHAVIOR TRACKING - Generic
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Track một hành vi của user (generic method)
     * Gọi khi: view product, click, add to cart, purchase, review, etc.
     */
    void trackBehavior(Integer userId, Integer productId, 
                       UserBehavior.ActionType actionType,
                       String sessionId,
                       UserBehavior.BehaviorSource source,
                       Integer viewDurationSeconds,
                       Integer quantity,
                       Integer rating);

    // ══════════════════════════════════════════════════════════════════════════
    // BEHAVIOR TRACKING - Specific Methods (Business Logic)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Track view product
     */
    void trackView(Integer userId, Integer productId, String sessionId, 
                   UserBehavior.BehaviorSource source, Integer durationSeconds);

    /**
     * Track generic click (backward compatible)
     */
    void trackClick(Integer userId, Integer productId, String sessionId,
                    UserBehavior.BehaviorSource source);

    /**
     * Track click from search results → SEARCH_CLICK action type
     * Source: SEARCH
     */
    void trackSearchClick(Integer userId, Integer productId, String sessionId);

    /**
     * Track click from recommendation section → RECOMMEND_CLICK action type
     * Source: RECOMMENDATION
     */
    void trackRecommendClick(Integer userId, Integer productId, String sessionId);

    /**
     * Track click from home page → CLICK action type
     * Source: HOME
     */
    void trackHomeClick(Integer userId, Integer productId, String sessionId);

    /**
     * Track click from category page → CLICK action type
     * Source: CATEGORY
     */
    void trackCategoryClick(Integer userId, Integer productId, String sessionId);

    /**
     * Track click from similar products section → CLICK action type
     * Source: SIMILAR
     */
    void trackSimilarClick(Integer userId, Integer productId, String sessionId);

    /**
     * Track add to cart
     */
    void trackAddCart(Integer userId, Integer productId, String sessionId, Integer quantity);

    /**
     * Track remove from cart
     */
    void trackRemoveCart(Integer userId, Integer productId, String sessionId);

    /**
     * Track wishlist add
     */
    void trackWishlistAdd(Integer userId, Integer productId, String sessionId);

    /**
     * Track wishlist remove
     */
    void trackWishlistRemove(Integer userId, Integer productId, String sessionId);

    /**
     * Track purchase
     */
    void trackPurchase(Integer userId, Integer productId, Integer quantity);

    /**
     * Track review
     */
    void trackReview(Integer userId, Integer productId, Integer rating);

    /**
     * Update view duration for existing VIEW behavior record
     * Used to update duration when user leaves product detail page
     */
    void updateViewDuration(com.example.FoodTourApp.entity.User user, Integer productId, String sessionId, Integer durationSeconds);

    // ══════════════════════════════════════════════════════════════════════════
    // REAL-TIME RECOMMENDATIONS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Lấy recommendations cho user (real-time)
     * Kết hợp:
     * - Recent behaviors (session-based)
     * - User long-term preferences
     * - Collaborative filtering (similar users)
     * - Content-based (similar products)
     */
    List<ProductResponseDTO> getRecommendations(Integer userId, String sessionId, int limit);

    /**
     * Lấy recommendations cho anonymous user (chỉ dựa vào session)
     */
    List<ProductResponseDTO> getSessionRecommendations(String sessionId, int limit);

    /**
     * Lấy similar products (content-based)
     */
    List<ProductResponseDTO> getSimilarProducts(Integer productId, int limit);

    /**
     * Lấy "Vì bạn đã xem X" recommendations
     * Dựa trên product vừa xem
     */
    List<ProductResponseDTO> getBecauseYouViewed(Integer userId, Integer viewedProductId, int limit);

    /**
     * Lấy "Người mua X cũng mua" recommendations
     * Collaborative filtering đơn giản
     */
    List<ProductResponseDTO> getAlsoBought(Integer productId, int limit);

    // ══════════════════════════════════════════════════════════════════════════
    // USER EMBEDDING MANAGEMENT
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Cập nhật user embedding trong Redis cache
     * Gọi sau mỗi behavior mới
     */
    void updateUserEmbedding(Integer userId);

    /**
     * Invalidate user embedding cache (force recalculate)
     */
    void invalidateUserEmbedding(Integer userId);

    /**
     * Precompute embeddings cho tất cả users (batch job)
     */
    void precomputeAllUserEmbeddings();

    /**
     * Hot-reload product embeddings từ file model_output/product_embeddings.json.
     * Gọi sau khi retrain Two-Tower model trên Colab và copy file mới vào resources.
     * Không cần restart app.
     *
     * @return số lượng embeddings được load
     */
    int reloadProductEmbeddings();

    // ══════════════════════════════════════════════════════════════════════════
    // SCHEDULED JOBS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Scheduled job: Refresh product embeddings từ ES mỗi 2 tiếng.
     * Tự động pick up embeddings mới sau mỗi lần retrain mà không cần restart app.
     */
    void scheduledRefreshEmbeddings();

    /**
     * Scheduled job: Precompute user embeddings mỗi 6 tiếng.
     * Warm up cache để cải thiện response time cho lần request đầu tiên.
     */
    void scheduledPrecomputeEmbeddings();

    // ══════════════════════════════════════════════════════════════════════════
    // PRODUCT SYNC SUPPORT - Gọi bởi EsRecommendSyncConsumer
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Lấy category-average embedding để dùng làm proxy cho product mới.
     * Trả về null nếu category chưa có embedding (category mới chưa có product nào).
     */
    float[] getCategoryEmbedding(Integer categoryId);

    /**
     * Thêm/cập nhật product vào in-memory maps ngay lập tức (không chờ scheduled refresh).
     * Gọi sau khi index product vào ES để KNN hoạt động ngay.
     * embedding có thể null nếu chưa có category average.
     */
    void registerProduct(Integer productId, Integer categoryId, Integer shopId, float[] embedding);

    /**
     * Xóa product khỏi in-memory maps khi product bị delete.
     */
    void removeProduct(Integer productId);
}
