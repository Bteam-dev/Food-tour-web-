package com.example.FoodTourApp.controller.UserController;

import com.example.FoodTourApp.DTO.CartDTO.CartItemResponseDTO;
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
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user/cart")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER','SELLER','ADMIN')")
public class UserCartController {

    private final CartService cartService;
    private static final Logger logger = LoggerFactory.getLogger(UserSellerApprovalController.class);

    @PostMapping
    public ResponseEntity<?> addToCart(@Valid @RequestBody CreateCartItemRequestDTO request, @AuthenticationPrincipal User user) {
        logger.info("User ID {} is adding product to cart: {}", user.getId(), request.getProductId());
        try {
            CartItemResponseDTO cartItem = cartService.addToCart(request, user);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Product added to cart successfully");
            result.put("data", cartItem);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error adding product to cart for user ID {}: {}", user.getId(), e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PutMapping("/{cartItemId}")
    public ResponseEntity<?> updateCartItem(@PathVariable Integer cartItemId, @Valid @RequestBody UpdateCartItemRequestDTO request, @AuthenticationPrincipal User user) {
        logger.info("User ID {} is updating cart item: {}", user.getId(), cartItemId);
        try {
            CartItemResponseDTO cartItem = cartService.updateCartItem(cartItemId, request, user);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", cartItem == null ? "Cart item deleted due to quantity 0" : "Cart item updated successfully");
            result.put("data", cartItem);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error updating cart item {} for user ID {}: {}", cartItemId, user.getId(), e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @DeleteMapping("/{cartItemId}")
    public ResponseEntity<?> removeFromCart(@PathVariable Integer cartItemId, @AuthenticationPrincipal User user) {
        logger.info("User ID {} is removing cart item: {}", user.getId(), cartItemId);
        try {
            cartService.removeFromCart(cartItemId, user);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Cart item removed successfully");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error removing cart item {} for user ID {}: {}", cartItemId, user.getId(), e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @DeleteMapping("/clear")
    public ResponseEntity<?> clearCart(@AuthenticationPrincipal User user) {
        logger.info("User ID {} is clearing cart", user.getId());
        try {
            cartService.clearCart(user);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Cart cleared successfully");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error clearing cart for user ID {}: {}", user.getId(), e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping
    public ResponseEntity<?> getCartItems(@AuthenticationPrincipal User user) {
        logger.info("User ID {} is getting cart items", user.getId());
        try {
            List<CartItemResponseDTO> cartItems = cartService.getCartItems(user);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", cartItems);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error getting cart items for user {}: {}", user.getEmail(), e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}
