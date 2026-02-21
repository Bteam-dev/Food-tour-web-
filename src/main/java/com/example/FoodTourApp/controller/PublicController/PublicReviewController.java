package com.example.FoodTourApp.controller.PublicController;

import com.example.FoodTourApp.DTO.ReviewDTO.ReviewReplyResponse;
import com.example.FoodTourApp.DTO.ReviewDTO.ReviewResponse;
import com.example.FoodTourApp.DTO.ReviewDTO.ReviewStatistics;
import com.example.FoodTourApp.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/public/reviews")
@RequiredArgsConstructor
@Slf4j
public class PublicReviewController {

    private final ReviewService reviewService;

    /**
     * Lấy danh sách review của SHOP (Public)
     * GET /api/public/reviews/shops/{shopId}
     */
    @GetMapping("/shops/{shopId}")
    public ResponseEntity<?> getShopReviews(
            @PathVariable Integer shopId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {

        log.info("Getting shop reviews for shop ID {}", shopId);

        try {
            Sort sort = sortDir.equalsIgnoreCase("ASC") ?
                    Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
            Pageable pageable = PageRequest.of(page, size, sort);

            Page<ReviewResponse> reviews = reviewService.getReviewsByReviewable("shop", shopId, pageable);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", reviews.getContent());
            result.put("currentPage", reviews.getNumber());
            result.put("totalItems", reviews.getTotalElements());
            result.put("totalPages", reviews.getTotalPages());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.error("Error getting shop reviews: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            log.error("Unexpected error getting shop reviews: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Có lỗi xảy ra khi lấy danh sách đánh giá shop");
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Lấy danh sách review của PRODUCT (Public)
     * GET /api/public/reviews/products/{productId}
     */
    @GetMapping("/products/{productId}")
    public ResponseEntity<?> getProductReviews(
            @PathVariable Integer productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {

        log.info("Getting product reviews for product ID {}", productId);

        try {
            Sort sort = sortDir.equalsIgnoreCase("ASC") ?
                    Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
            Pageable pageable = PageRequest.of(page, size, sort);

            Page<ReviewResponse> reviews = reviewService.getReviewsByReviewable("product", productId, pageable);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", reviews.getContent());
            result.put("currentPage", reviews.getNumber());
            result.put("totalItems", reviews.getTotalElements());
            result.put("totalPages", reviews.getTotalPages());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.error("Error getting product reviews: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            log.error("Unexpected error getting product reviews: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Có lỗi xảy ra khi lấy danh sách đánh giá sản phẩm");
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Lấy thống kê rating của SHOP (Public)
     * GET /api/public/reviews/shops/{shopId}/statistics
     */
    @GetMapping("/shops/{shopId}/statistics")
    public ResponseEntity<?> getShopReviewStatistics(
            @PathVariable Integer shopId) {

        log.info("Getting review statistics for shop ID {}", shopId);

        try {
            ReviewStatistics statistics = reviewService.getReviewStatistics("shop", shopId);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", statistics);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.error("Error getting shop statistics: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            log.error("Unexpected error getting shop statistics: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Có lỗi xảy ra khi lấy thống kê đánh giá shop");
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Lấy thống kê rating của PRODUCT (Public)
     * GET /api/public/reviews/products/{productId}/statistics
     */
    @GetMapping("/products/{productId}/statistics")
    public ResponseEntity<?> getProductReviewStatistics(
            @PathVariable Integer productId) {

        log.info("Getting review statistics for product ID {}", productId);

        try {
            ReviewStatistics statistics = reviewService.getReviewStatistics("product", productId);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", statistics);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.error("Error getting product statistics: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            log.error("Unexpected error getting product statistics: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Có lỗi xảy ra khi lấy thống kê đánh giá sản phẩm");
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Xem chi tiết 1 đánh giá
     * GET /api/public/reviews/{reviewId}
     */
    @GetMapping("/{reviewId}")
    public ResponseEntity<?> getReviewById(@PathVariable Integer reviewId) {

        log.info("Getting review ID {}", reviewId);

        try {
            ReviewResponse review = reviewService.getReviewById(reviewId);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", review);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error getting review: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Không tìm thấy đánh giá");
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Lấy danh sách tất cả reply của 1 review (PUBLIC - ai cũng xem được)
     * GET /api/public/reviews/{reviewId}/replies
     */
    @GetMapping("/{reviewId}/replies")
    public ResponseEntity<?> getRepliesByReviewId(
            @PathVariable Integer reviewId) {

        log.info("Getting all replies for review ID {}", reviewId);

        try {
            List<ReviewReplyResponse> replies = reviewService.getRepliesByReviewId(reviewId);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", replies);
            result.put("totalReplies", replies.size());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error getting replies: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}
