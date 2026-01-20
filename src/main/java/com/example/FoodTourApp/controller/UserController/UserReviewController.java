package com.example.FoodTourApp.controller.UserController;

import com.example.FoodTourApp.DTO.ReviewDTO.*;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user/reviews")
@PreAuthorize("hasAnyRole('USER', 'SELLER', 'ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class UserReviewController {

    private final ReviewService reviewService;

    /**
     * Tạo review cho SHOP (sau khi mua hàng)
     * POST /api/user/reviews/shops/{shopId}
     */
    @PostMapping("/shops/{shopId}")
    public ResponseEntity<?> createShopReview(
            @PathVariable Integer shopId,
            @Valid @RequestPart("data") CreateReviewRequest request,
            @RequestPart(value = "images", required = false) MultipartFile[] images,
            @AuthenticationPrincipal User user) {

        log.info("User ID {} is creating shop review for shop ID {}", user.getId(), shopId);

        try {
            // Override để đảm bảo đúng loại
            request.setReviewableType("shop");
            request.setReviewableId(shopId);

            ReviewResponse response = reviewService.createReview(request, images, user);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Đánh giá shop thành công");
            result.put("data", response);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.error("Error creating shop review: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            log.error("Unexpected error creating shop review: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Có lỗi xảy ra khi tạo đánh giá shop");
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Tạo review cho PRODUCT (sau khi mua hàng và nhận được sản phẩm)
     * POST /api/user/reviews/products/{productId}/orders/{orderId}
     */
    @PostMapping("/products/{productId}/orders/{orderId}")
    public ResponseEntity<?> createProductReview(
            @PathVariable Integer productId,
            @PathVariable Integer orderId,
            @Valid @RequestPart("data") CreateReviewRequest request,
            @RequestPart(value = "images", required = false) MultipartFile[] images,
            @AuthenticationPrincipal User user) {

        log.info("User ID {} is creating product review for product ID {} from order ID {}",
                user.getId(), productId, orderId);

        try {
            // Override để đảm bảo đúng loại
            request.setReviewableType("product");
            request.setReviewableId(productId);
            request.setOrderId(orderId);

            ReviewResponse response = reviewService.createReview(request, images, user);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Đánh giá sản phẩm thành công");
            result.put("data", response);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.error("Error creating product review: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            log.error("Unexpected error creating product review: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Có lỗi xảy ra khi tạo đánh giá sản phẩm");
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Cập nhật review của mình
     * PUT /api/user/reviews/{reviewId}
     */
    @PutMapping("/{reviewId}")
    public ResponseEntity<?> updateReview(
            @PathVariable Integer reviewId,
            @Valid @RequestPart("data") UpdateReviewRequest request,
            @RequestPart(value = "images", required = false) MultipartFile[] images,
            @AuthenticationPrincipal User user) {

        log.info("User ID {} is updating review ID {}", user.getId(), reviewId);

        try {
            ReviewResponse response = reviewService.updateReview(reviewId, request, images, user);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Cập nhật đánh giá thành công");
            result.put("data", response);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.error("Error updating review: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            log.error("Unexpected error updating review: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Có lỗi xảy ra khi cập nhật đánh giá");
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Xóa review của mình
     * DELETE /api/user/reviews/{reviewId}
     */
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<?> deleteReview(
            @PathVariable Integer reviewId,
            @AuthenticationPrincipal User user) {

        log.info("User ID {} is deleting review ID {}", user.getId(), reviewId);

        try {
            reviewService.deleteReview(reviewId, user);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Xóa đánh giá thành công");
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.error("Error deleting review: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            log.error("Unexpected error deleting review: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Có lỗi xảy ra khi xóa đánh giá");
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Lấy danh sách review của mình
     * GET /api/user/reviews/my-reviews
     */
    @GetMapping("/my-reviews")
    public ResponseEntity<?> getMyReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir,
            @AuthenticationPrincipal User user) {

        log.info("User ID {} is getting their reviews", user.getId());

        try {
            Sort sort = sortDir.equalsIgnoreCase("ASC") ?
                    Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
            Pageable pageable = PageRequest.of(page, size, sort);

            Page<ReviewResponse> reviews = reviewService.getMyReviews(user, pageable);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", reviews.getContent());
            result.put("currentPage", reviews.getNumber());
            result.put("totalItems", reviews.getTotalElements());
            result.put("totalPages", reviews.getTotalPages());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error getting my reviews: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Có lỗi xảy ra khi lấy danh sách đánh giá");
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Trả lời review (ai cũng reply được - giống Facebook comment)
     * POST /api/user/reviews/{reviewId}/reply
     */
    @PostMapping("/{reviewId}/reply")
    public ResponseEntity<?> replyReview(
            @PathVariable Integer reviewId,
            @Valid @RequestBody ReplyRequest request,
            @AuthenticationPrincipal User user) {

        log.info("User ID {} is replying to review ID {}", user.getId(), reviewId);

        try {
            ReviewResponse response = reviewService.replyReview(reviewId, request, user);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Đã trả lời đánh giá");
            result.put("data", response);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error replying to review: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Có lỗi xảy ra khi trả lời đánh giá");
            return ResponseEntity.badRequest().body(error);
        }
    }
}
