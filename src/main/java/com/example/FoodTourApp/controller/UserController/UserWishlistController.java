package com.example.FoodTourApp.controller.UserController;

import com.example.FoodTourApp.DTO.PageResponse;
import com.example.FoodTourApp.DTO.WishlistDTO.AddToWishlistRequest;
import com.example.FoodTourApp.DTO.WishlistDTO.WishlistItemDTO;
import com.example.FoodTourApp.DTO.WishlistDTO.WishlistResponse;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.service.WishlistService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Map;

@RestController
@RequestMapping("/api/user/wishlist")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER','SELLER','ADMIN')")
public class UserWishlistController {

    private final WishlistService wishlistService;
    private static final Logger logger = LoggerFactory.getLogger(UserWishlistController.class);

    /**
     * Lấy danh sách wishlist của user (có phân trang)
     * GET /api/user/wishlist?page=0&size=10
     */
    @GetMapping
    public ResponseEntity<PageResponse<WishlistItemDTO>> getUserWishlist(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal User user) {
        logger.info("User ID {} is getting wishlist, page: {}, size: {}", user.getId(), page, size);
        PageResponse<WishlistItemDTO> wishlist = wishlistService.getUserWishlist(user.getId(), page, size);
        return ResponseEntity.ok(wishlist);
    }

    /**
     * Thêm sản phẩm vào wishlist
     * POST /api/user/wishlist
     */
    @PostMapping
    public ResponseEntity<WishlistResponse> addToWishlist(
            @Valid @RequestBody AddToWishlistRequest request,
            @AuthenticationPrincipal User user) {
        logger.info("User ID {} is adding product {} to wishlist", user.getId(), request.getProductId());
        try {
            WishlistResponse response = wishlistService.addToWishlist(user.getId(), request.getProductId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error adding to wishlist for user ID {}: {}", user.getId(), e.getMessage(), e);
            return ResponseEntity.badRequest().body(new WishlistResponse(e.getMessage(), false, null, 0L));
        }
    }

    /**
     * Xóa sản phẩm khỏi wishlist
     * DELETE /api/user/wishlist/{productId}
     */
    @DeleteMapping("/{productId}")
    public ResponseEntity<WishlistResponse> removeFromWishlist(
            @PathVariable Integer productId,
            @AuthenticationPrincipal User user) {
        logger.info("User ID {} is removing product {} from wishlist", user.getId(), productId);
        try {
            WishlistResponse response = wishlistService.removeFromWishlist(user.getId(), productId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error removing from wishlist for user ID {}: {}", user.getId(), e.getMessage(), e);
            return ResponseEntity.badRequest().body(new WishlistResponse(e.getMessage(), false, null, 0L));
        }
    }

    /**
     * Toggle wishlist: thêm nếu chưa có, xóa nếu đã có
     * POST /api/user/wishlist/toggle/{productId}
     * FE dùng khi bấm icon trái tim – nếu đã có thì xóa, chưa có thì thêm
     */
    @PostMapping("/toggle/{productId}")
    public ResponseEntity<WishlistResponse> toggleWishlist(
            @PathVariable Integer productId,
            @AuthenticationPrincipal User user) {
        logger.info("User ID {} is toggling product {} in wishlist", user.getId(), productId);
        try {
            WishlistResponse response = wishlistService.toggleWishlist(user.getId(), productId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error toggling wishlist for user ID {}: {}", user.getId(), e.getMessage(), e);
            return ResponseEntity.badRequest().body(new WishlistResponse(e.getMessage(), false, null, 0L));
        }
    }

    /**
     * Kiểm tra sản phẩm đã có trong wishlist chưa
     * GET /api/user/wishlist/check/{productId}
     * FE dùng để tô màu trái tim khi mở trang chi tiết sản phẩm
     */
    @GetMapping("/check/{productId}")
    public ResponseEntity<Map<String, Object>> checkWishlist(
            @PathVariable Integer productId,
            @AuthenticationPrincipal User user) {
        logger.info("User ID {} is checking product {} in wishlist", user.getId(), productId);
        boolean inWishlist = wishlistService.isInWishlist(user.getId(), productId);
        return ResponseEntity.ok(Map.of("productId", productId, "inWishlist", inWishlist));
    }

    /**
     * Đếm số lượng sản phẩm trong wishlist
     * GET /api/user/wishlist/count
     * FE dùng hiển thị badge số lượng trên icon wishlist
     */
    @GetMapping("/count")
    public ResponseEntity<Map<String, Object>> countWishlistItems(@AuthenticationPrincipal User user) {
        logger.info("User ID {} is getting wishlist count", user.getId());
        long count = wishlistService.countWishlistItems(user.getId());
        return ResponseEntity.ok(Map.of("count", count));
    }

    /**
     * Xóa toàn bộ wishlist
     * DELETE /api/user/wishlist
     */
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> clearWishlist(@AuthenticationPrincipal User user) {
        logger.info("User ID {} is clearing wishlist", user.getId());
        try {
            wishlistService.clearWishlist(user.getId());
            return ResponseEntity.ok(Map.of("success", true, "message", "Đã xóa toàn bộ danh sách yêu thích"));
        } catch (Exception e) {
            logger.error("Error clearing wishlist for user ID {}: {}", user.getId(), e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}
