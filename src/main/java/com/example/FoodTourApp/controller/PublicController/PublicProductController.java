package com.example.FoodTourApp.controller.PublicController;

import com.example.FoodTourApp.DTO.PageResponse;
import com.example.FoodTourApp.DTO.ProductDTO.ProductResponseDTO;
import com.example.FoodTourApp.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
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
    public ResponseEntity<?> getAllActiveProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        logger.info("Fetching all active products with pagination - page: {}, size: {}", page, size);

        try {
            Sort sort = sortDir.equalsIgnoreCase("ASC") ?
                    Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
            Pageable pageable = PageRequest.of(page, size, sort);

            Page<ProductResponseDTO> products = productService.getAllActiveProducts(pageable);
            PageResponse<ProductResponseDTO> pageResponse = PageResponse.of(products);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", pageResponse);
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
    public ResponseEntity<?> getActiveProductsByShop(
            @PathVariable Integer shopId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        logger.info("Fetching active products for shop: {} with pagination", shopId);

        try {
            Sort sort = sortDir.equalsIgnoreCase("ASC") ?
                    Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
            Pageable pageable = PageRequest.of(page, size, sort);

            Page<ProductResponseDTO> products = productService.getActiveProductsByShop(shopId, pageable);
            PageResponse<ProductResponseDTO> pageResponse = PageResponse.of(products);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", pageResponse);
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
    public ResponseEntity<?> getProductsByCategory(
            @PathVariable Integer categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        logger.info("Fetching products for category: {} with pagination", categoryId);

        try {
            Sort sort = sortDir.equalsIgnoreCase("ASC") ?
                    Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
            Pageable pageable = PageRequest.of(page, size, sort);

            Page<ProductResponseDTO> products = productService.getProductsByCategory(categoryId, pageable);
            PageResponse<ProductResponseDTO> pageResponse = PageResponse.of(products);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", pageResponse);
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
