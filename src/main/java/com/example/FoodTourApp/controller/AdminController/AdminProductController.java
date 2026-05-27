package com.example.FoodTourApp.controller.AdminController;

import com.example.FoodTourApp.DTO.ProductDTO.ProductResponseDTO;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin Product Controller - Quản lý sản phẩm trên toàn hệ thống
 */
@RestController
@RequestMapping("/api/admin/products")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminProductController {

    private static final Logger logger = LoggerFactory.getLogger(AdminProductController.class);
    private final ProductService productService;

    /**
     * Lấy tất cả sản phẩm (kể cả inactive)
     * GET /api/admin/products
     */
    @GetMapping
    public ResponseEntity<?> getAllProducts(@AuthenticationPrincipal User user) {
        logger.info("Admin {} is fetching all products", user.getEmail());
        try {
            List<ProductResponseDTO> products = productService.getAllProducts();
            return ResponseEntity.ok(Map.of("success", true, "data", products));
        } catch (Exception e) {
            logger.error("Error fetching all products: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(
                    Map.of("success", false, "message", "Failed to fetch products"));
        }
    }
}
