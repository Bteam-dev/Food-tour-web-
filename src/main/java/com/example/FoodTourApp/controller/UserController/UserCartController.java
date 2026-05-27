package com.example.FoodTourApp.controller.UserController;

import com.example.FoodTourApp.DTO.CartDTO.CartItemResponseDTO;
import com.example.FoodTourApp.DTO.CartDTO.CartResponseDTO;
import com.example.FoodTourApp.DTO.CartDTO.CreateCartItemRequestDTO;
import com.example.FoodTourApp.DTO.CartDTO.UpdateCartItemRequestDTO;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.service.CartService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user/cart")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER','SELLER','ADMIN')")
public class UserCartController {

    private final CartService cartService;
    private static final Logger logger = LoggerFactory.getLogger(UserCartController.class);

    /**
     * GET /api/user/cart
     * Lấy toàn bộ giỏ hàng, đã group theo shop
     */
    @GetMapping
    public ResponseEntity<?> getCart(@AuthenticationPrincipal User user) {
        logger.info("User {} getting cart", user.getId());
        try {
            CartResponseDTO cart = cartService.getCart(user);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", cart);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error getting cart for user {}: {}", user.getId(), e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * POST /api/user/cart/items
     * Thêm sản phẩm vào giỏ hàng
     */
    @PostMapping("/items")
    public ResponseEntity<?> addToCart(@Valid @RequestBody CreateCartItemRequestDTO request,
                                       @AuthenticationPrincipal User user) {
        logger.info("User {} adding product {} to cart", user.getId(), request.getProductId());
        try {
            CartItemResponseDTO item = cartService.addToCart(request, user);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Product added to cart successfully");
            result.put("data", item);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error adding to cart for user {}: {}", user.getId(), e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * PUT /api/user/cart/items/{cartItemId}
     * Cập nhật số lượng / variants / ghi chú của 1 item
     * quantity = 0 → tự động xóa item
     */
    @PutMapping("/items/{cartItemId}")
    public ResponseEntity<?> updateCartItem(@PathVariable Integer cartItemId,
                                            @Valid @RequestBody UpdateCartItemRequestDTO request,
                                            @AuthenticationPrincipal User user) {
        logger.info("User {} updating cart item {}", user.getId(), cartItemId);
        try {
            CartItemResponseDTO item = cartService.updateCartItem(cartItemId, request, user);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", item == null ? "Cart item removed (quantity = 0)" : "Cart item updated successfully");
            result.put("data", item);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error updating cart item {} for user {}: {}", cartItemId, user.getId(), e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * DELETE /api/user/cart/items/{cartItemId}
     * Xóa 1 item khỏi giỏ
     */
    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<?> removeCartItem(@PathVariable Integer cartItemId,
                                            @AuthenticationPrincipal User user) {
        logger.info("User {} removing cart item {}", user.getId(), cartItemId);
        try {
            cartService.removeCartItem(cartItemId, user);
            return ResponseEntity.ok(Map.of("success", true, "message", "Cart item removed successfully"));
        } catch (Exception e) {
            logger.error("Error removing cart item {} for user {}: {}", cartItemId, user.getId(), e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * DELETE /api/user/cart
     * Xóa toàn bộ giỏ hàng
     */
    @DeleteMapping
    public ResponseEntity<?> clearCart(@AuthenticationPrincipal User user) {
        logger.info("User {} clearing cart", user.getId());
        try {
            cartService.clearCart(user);
            return ResponseEntity.ok(Map.of("success", true, "message", "Cart cleared successfully"));
        } catch (Exception e) {
            logger.error("Error clearing cart for user {}: {}", user.getId(), e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}
