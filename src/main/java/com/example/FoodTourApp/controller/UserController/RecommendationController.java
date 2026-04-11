package com.example.FoodTourApp.controller.UserController;

import com.example.FoodTourApp.DTO.BehaviorDTO.TrackBehaviorRequest;
import com.example.FoodTourApp.DTO.ProductDTO.ProductResponseDTO;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.entity.UserBehavior.BehaviorSource;
import com.example.FoodTourApp.service.RealTimeRecommendationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller cho Real-time Recommendation System
 * 
 * Kiến trúc giống YouTube:
 * 1. Track mọi hành vi user real-time
 * 2. Cập nhật user embedding ngay lập tức
 * 3. Gợi ý thay đổi theo hành vi mới nhất
 * 
 * Note: Controller chỉ handle HTTP request/response.
 * Business logic nằm trong RealTimeRecommendationService.
 */
@RestController
@RequestMapping("/api/user/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RealTimeRecommendationService recommendationService;
    private static final Logger logger = LoggerFactory.getLogger(RecommendationController.class);

    // ══════════════════════════════════════════════════════════════════════════
    // RECOMMENDATION ENDPOINTS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * GET /api/user/recommendations/for-you?limit=10&sessionId=xxx
     */
    @GetMapping("/for-you")
    public ResponseEntity<?> getForYou(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String sessionId,
            @RequestParam(defaultValue = "10") int limit
    ) {
        logger.info("User {} getting real-time recommendations (session={})", user.getId(), sessionId);
        try {
            List<ProductResponseDTO> recs = recommendationService.getRecommendations(user.getId(), sessionId, limit);
            return ResponseEntity.ok(Map.of("success", true, "data", recs, "type", "personalized"));
        } catch (Exception e) {
            logger.error("Error getting recommendations for user {}: {}", user.getId(), e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * GET /api/user/recommendations/for-user (backward compatible)
     */
    @GetMapping("/for-user")
    public ResponseEntity<?> getForUser(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String sessionId,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return getForYou(user, sessionId, limit);
    }

    /**
     * GET /api/user/recommendations/similar/{productId}?limit=6
     */
    @GetMapping("/similar/{productId}")
    public ResponseEntity<?> getSimilar(
            @PathVariable Integer productId,
            @RequestParam(defaultValue = "6") int limit
    ) {
        logger.info("Getting similar products for product {}", productId);
        try {
            List<ProductResponseDTO> similar = recommendationService.getSimilarProducts(productId, limit);
            return ResponseEntity.ok(Map.of("success", true, "data", similar));
        } catch (Exception e) {
            logger.error("Error getting similar products: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * GET /api/user/recommendations/because-you-viewed/{productId}?limit=6
     */
    @GetMapping("/because-you-viewed/{productId}")
    public ResponseEntity<?> getBecauseYouViewed(
            @AuthenticationPrincipal User user,
            @PathVariable Integer productId,
            @RequestParam(defaultValue = "6") int limit
    ) {
        logger.info("User {} getting 'because you viewed' for product {}", user.getId(), productId);
        try {
            List<ProductResponseDTO> recs = recommendationService.getBecauseYouViewed(user.getId(), productId, limit);
            return ResponseEntity.ok(Map.of("success", true, "data", recs));
        } catch (Exception e) {
            logger.error("Error: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * GET /api/user/recommendations/also-bought/{productId}?limit=6
     */
    @GetMapping("/also-bought/{productId}")
    public ResponseEntity<?> getAlsoBought(
            @PathVariable Integer productId,
            @RequestParam(defaultValue = "6") int limit
    ) {
        logger.info("Getting 'also bought' for product {}", productId);
        try {
            List<ProductResponseDTO> recs = recommendationService.getAlsoBought(productId, limit);
            return ResponseEntity.ok(Map.of("success", true, "data", recs));
        } catch (Exception e) {
            logger.error("Error: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * GET /api/user/recommendations/session?sessionId=xxx&limit=10
     */
    @GetMapping("/session")
    public ResponseEntity<?> getSessionRecommendations(
            @RequestParam String sessionId,
            @RequestParam(defaultValue = "10") int limit
    ) {
        logger.info("Getting session recommendations for session {}", sessionId);
        try {
            List<ProductResponseDTO> recs = recommendationService.getSessionRecommendations(sessionId, limit);
            return ResponseEntity.ok(Map.of("success", true, "data", recs, "type", "session"));
        } catch (Exception e) {
            logger.error("Error: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BEHAVIOR TRACKING ENDPOINTS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * POST /api/user/recommendations/track
     * Generic tracking endpoint for custom behavior tracking
     */
    @PostMapping("/track")
    public ResponseEntity<?> trackBehavior(
            @AuthenticationPrincipal User user,
            @RequestBody TrackBehaviorRequest request
    ) {
        logger.debug("Tracking {} for user {} on product {}", 
                request.getActionType(), user.getId(), request.getProductId());
        try {
            recommendationService.trackBehavior(
                    user.getId(),
                    request.getProductId(),
                    request.getActionType(),
                    request.getSessionId(),
                    request.getSource(),
                    request.getViewDurationSeconds(),
                    request.getQuantity(),
                    request.getRating()
            );
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            logger.error("Error tracking behavior: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * POST /api/user/recommendations/track/view
     */
    @PostMapping("/track/view")
    public ResponseEntity<?> trackView(
            @AuthenticationPrincipal User user,
            @RequestParam Integer productId,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Integer durationSeconds
    ) {
        try {
            BehaviorSource src = source != null ? BehaviorSource.valueOf(source) : BehaviorSource.DIRECT;
            recommendationService.trackView(user.getId(), productId, sessionId, src, durationSeconds);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * POST /api/user/recommendations/track/click
     * Generic click tracking (backward compatible)
     */
    @PostMapping("/track/click")
    public ResponseEntity<?> trackClick(
            @AuthenticationPrincipal User user,
            @RequestParam Integer productId,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String source
    ) {
        try {
            BehaviorSource src = source != null ? BehaviorSource.valueOf(source) : BehaviorSource.DIRECT;
            recommendationService.trackClick(user.getId(), productId, sessionId, src);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * POST /api/user/recommendations/track/search-click
     * Track click from search results (uses SEARCH_CLICK action type with higher weight)
     */
    @PostMapping("/track/search-click")
    public ResponseEntity<?> trackSearchClick(
            @AuthenticationPrincipal User user,
            @RequestParam Integer productId,
            @RequestParam(required = false) String sessionId
    ) {
        try {
            recommendationService.trackSearchClick(user.getId(), productId, sessionId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * POST /api/user/recommendations/track/recommend-click
     * Track click from recommendation section (uses RECOMMEND_CLICK action type with highest click weight)
     */
    @PostMapping("/track/recommend-click")
    public ResponseEntity<?> trackRecommendClick(
            @AuthenticationPrincipal User user,
            @RequestParam Integer productId,
            @RequestParam(required = false) String sessionId
    ) {
        try {
            recommendationService.trackRecommendClick(user.getId(), productId, sessionId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * POST /api/user/recommendations/track/home-click
     * Track click from home page
     */
    @PostMapping("/track/home-click")
    public ResponseEntity<?> trackHomeClick(
            @AuthenticationPrincipal User user,
            @RequestParam Integer productId,
            @RequestParam(required = false) String sessionId
    ) {
        try {
            recommendationService.trackHomeClick(user.getId(), productId, sessionId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * POST /api/user/recommendations/track/category-click
     * Track click from category page
     */
    @PostMapping("/track/category-click")
    public ResponseEntity<?> trackCategoryClick(
            @AuthenticationPrincipal User user,
            @RequestParam Integer productId,
            @RequestParam(required = false) String sessionId
    ) {
        try {
            recommendationService.trackCategoryClick(user.getId(), productId, sessionId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * POST /api/user/recommendations/track/similar-click
     * Track click from similar products section
     */
    @PostMapping("/track/similar-click")
    public ResponseEntity<?> trackSimilarClick(
            @AuthenticationPrincipal User user,
            @RequestParam Integer productId,
            @RequestParam(required = false) String sessionId
    ) {
        try {
            recommendationService.trackSimilarClick(user.getId(), productId, sessionId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * POST /api/user/recommendations/track/add-cart
     */
    @PostMapping("/track/add-cart")
    public ResponseEntity<?> trackAddCart(
            @AuthenticationPrincipal User user,
            @RequestParam Integer productId,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false, defaultValue = "1") Integer quantity
    ) {
        try {
            recommendationService.trackAddCart(user.getId(), productId, sessionId, quantity);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * POST /api/user/recommendations/track/remove-cart
     */
    @PostMapping("/track/remove-cart")
    public ResponseEntity<?> trackRemoveCart(
            @AuthenticationPrincipal User user,
            @RequestParam Integer productId,
            @RequestParam(required = false) String sessionId
    ) {
        try {
            recommendationService.trackRemoveCart(user.getId(), productId, sessionId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * POST /api/user/recommendations/track/wishlist-add
     */
    @PostMapping("/track/wishlist-add")
    public ResponseEntity<?> trackWishlistAdd(
            @AuthenticationPrincipal User user,
            @RequestParam Integer productId,
            @RequestParam(required = false) String sessionId
    ) {
        try {
            recommendationService.trackWishlistAdd(user.getId(), productId, sessionId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * POST /api/user/recommendations/track/wishlist-remove
     */
    @PostMapping("/track/wishlist-remove")
    public ResponseEntity<?> trackWishlistRemove(
            @AuthenticationPrincipal User user,
            @RequestParam Integer productId,
            @RequestParam(required = false) String sessionId
    ) {
        try {
            recommendationService.trackWishlistRemove(user.getId(), productId, sessionId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * POST /api/user/recommendations/track/purchase
     */
    @PostMapping("/track/purchase")
    public ResponseEntity<?> trackPurchase(
            @AuthenticationPrincipal User user,
            @RequestParam Integer productId,
            @RequestParam(required = false, defaultValue = "1") Integer quantity
    ) {
        try {
            recommendationService.trackPurchase(user.getId(), productId, quantity);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * POST /api/user/recommendations/track/review
     */
    @PostMapping("/track/review")
    public ResponseEntity<?> trackReview(
            @AuthenticationPrincipal User user,
            @RequestParam Integer productId,
            @RequestParam Integer rating
    ) {
        try {
            recommendationService.trackReview(user.getId(), productId, rating);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CACHE MANAGEMENT ENDPOINTS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * POST /api/user/recommendations/invalidate-cache
     */
    @PostMapping("/invalidate-cache")
    public ResponseEntity<?> invalidateCache(@AuthenticationPrincipal User user) {
        try {
            recommendationService.invalidateUserEmbedding(user.getId());
            return ResponseEntity.ok(Map.of("success", true, "message", "Cache invalidated"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * POST /api/user/recommendations/update-embedding
     */
    @PostMapping("/update-embedding")
    public ResponseEntity<?> updateEmbedding(@AuthenticationPrincipal User user) {
        try {
            recommendationService.updateUserEmbedding(user.getId());
            return ResponseEntity.ok(Map.of("success", true, "message", "Embedding update triggered"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * PUT /api/user/recommendations/track/view-duration?productId=123&sessionId=abc&durationSeconds=45
     * Update view duration for existing VIEW behavior record
     */
    @PutMapping("/track/view-duration")
    public ResponseEntity<?> updateViewDuration(
            @RequestParam Integer productId,
            @RequestParam String sessionId,
            @RequestParam Integer durationSeconds,
            @AuthenticationPrincipal User user
    ) {
        try {
            recommendationService.updateViewDuration(user, productId, sessionId, durationSeconds);
            return ResponseEntity.ok(Map.of("success", true, "message", "View duration updated"));
        } catch (Exception e) {
            logger.error("Error updating view duration: productId={}, sessionId={}, duration={}, error={}",
                    productId, sessionId, durationSeconds, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}
