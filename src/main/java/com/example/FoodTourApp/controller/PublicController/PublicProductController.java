package com.example.FoodTourApp.controller.PublicController;

import com.example.FoodTourApp.DTO.ProductDTO.ProductResponseDTO;
import com.example.FoodTourApp.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/public/products")
public class PublicProductController {

    private static final Logger logger = LoggerFactory.getLogger(PublicProductController.class);
    private final ProductService productService;

    public PublicProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public ResponseEntity<?> getAllActiveProducts() {
        logger.info("Fetching all active products");

        try {
            List<ProductResponseDTO> products = productService.getAllActiveProducts();

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", products);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error fetching active products: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to fetch products");
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getProductById(@PathVariable Integer id) {
        logger.info("Fetching product with id: {}", id);

        try {
            ProductResponseDTO product = productService.getProductById(id);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", product);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error fetching product {}: {}", id, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/shop/{shopId}")
    public ResponseEntity<?> getActiveProductsByShop(@PathVariable Integer shopId) {
        logger.info("Fetching active products for shop: {}", shopId);

        try {
            List<ProductResponseDTO> products = productService.getActiveProductsByShop(shopId);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", products);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error fetching products for shop {}: {}", shopId, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to fetch products");
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @GetMapping("/category/{categoryId}")
    public ResponseEntity<?> getProductsByCategory(@PathVariable Integer categoryId) {
        logger.info("Fetching products for category: {}", categoryId);

        try {
            List<ProductResponseDTO> products = productService.getProductsByCategory(categoryId);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", products);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error fetching products for category {}: {}", categoryId, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to fetch products");
            return ResponseEntity.internalServerError().body(error);
        }
    }
}
