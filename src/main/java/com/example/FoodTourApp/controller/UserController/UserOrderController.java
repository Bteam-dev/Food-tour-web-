package com.example.FoodTourApp.controller.UserController;

import com.example.FoodTourApp.DTO.OrderDTO.CreateOrderRequestDTO;
import com.example.FoodTourApp.DTO.OrderDTO.OrderResponseDTO;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user/orders")
@RequiredArgsConstructor
public class UserOrderController {

    private final OrderService orderService;
    private static final Logger logger = LoggerFactory.getLogger(UserSellerApprovalController.class);

    @PostMapping
    public ResponseEntity<?> createOrder(@Valid @RequestBody CreateOrderRequestDTO request, @AuthenticationPrincipal User user) {
        logger.info("User {} is creating order with cartItemIds: {}", user.getEmail(), request.getCartItemIds());
        try {
            OrderResponseDTO order = orderService.createOrder(request, user);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Order created successfully");
            result.put("data", order);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error creating order for user {}: {}", user.getEmail(), e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping
    public ResponseEntity<?> getUserOrders(@AuthenticationPrincipal User user) {
        logger.info("User {} is getting orders", user.getEmail());
        try {
            List<OrderResponseDTO> orders = orderService.getUserOrders(user);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", orders);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error getting orders for user {}: {}", user.getEmail(), e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}