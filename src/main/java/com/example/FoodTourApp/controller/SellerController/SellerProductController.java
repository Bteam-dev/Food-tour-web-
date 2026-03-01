package com.example.FoodTourApp.controller.SellerController;

import com.example.FoodTourApp.DTO.ProductDTO.ProductResponseDTO;
import com.example.FoodTourApp.DTO.ProductVariantDTO.CreateVariantRequestDTO;
import com.example.FoodTourApp.DTO.ProductVariantDTO.UpdateVariantRequestDTO;
import com.example.FoodTourApp.DTO.ProductVariantDTO.VariantResponseDTO;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.List;
import java.util.Map;

/**
 * Controller quản lý sản phẩm theo SHOP
 * Pattern: /api/seller/shops/{shopId}/products
 * shopId lấy từ URL path, không cần điền trong request body
 */
@RestController
@RequestMapping("/api/seller/shops/{shopId}/products")
@PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
@RequiredArgsConstructor
public class SellerProductController {

    private static final Logger logger = LoggerFactory.getLogger(SellerProductController.class);
    private final ProductService productService;

    /**
     * Lấy danh sách sản phẩm của shop (có phân trang)
     * GET /api/seller/shops/{shopId}/products?page=0&size=10
     */
    @GetMapping
    public ResponseEntity<?> getShopProducts(
            @PathVariable Integer shopId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction,
            @AuthenticationPrincipal User user) {

        logger.info("Getting products for shop {} by user {}", shopId, user.getEmail());

        try {
            productService.validateShopOwnership(shopId, user);

            Sort.Direction sortDirection = direction.equalsIgnoreCase("ASC") ? Sort.Direction.ASC : Sort.Direction.DESC;
            Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));
            Page<ProductResponseDTO> products = productService.getActiveProductsByShop(shopId, pageable);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", products.getContent(),
                    "currentPage", products.getNumber(),
                    "totalItems", products.getTotalElements(),
                    "totalPages", products.getTotalPages()));
        } catch (Exception e) {
            logger.error("Error getting products for shop {}: {}", shopId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Tạo sản phẩm mới (upload ảnh sản phẩm dưới dạng form-data)
     * POST /api/seller/shops/{shopId}/products
     *
     * shopId từ URL path - KHÔNG CẦN điền trong request body
     */
    @PostMapping
    public ResponseEntity<?> createProduct(
            @PathVariable Integer shopId,
            @RequestParam(value = "data", required = false) String dataJson,
            @RequestParam(value = "images", required = false) MultipartFile[] images,
            @AuthenticationPrincipal User user) {

        logger.info("User {} is creating a product for shop {}", user.getEmail(), shopId);
        try {
            ProductResponseDTO response = productService.createProductWithImages(shopId, dataJson, images, user);
            return ResponseEntity.ok(Map.of("success", true, "message", "Product created successfully", "data", response));
        } catch (Exception e) {
            logger.error("Error creating product for shop {}: {}", shopId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Lấy chi tiết sản phẩm
     * GET /api/seller/shops/{shopId}/products/{productId}
     */
    @GetMapping("/{productId}")
    public ResponseEntity<?> getProduct(
            @PathVariable Integer shopId,
            @PathVariable Integer productId,
            @AuthenticationPrincipal User user) {

        logger.info("User {} is getting product {} from shop {}", user.getEmail(), productId, shopId);

        try {
            productService.validateShopOwnership(shopId, user);
            productService.validateProductBelongsToShop(productId, shopId);

            ProductResponseDTO product = productService.getProductById(productId);

            return ResponseEntity.ok(Map.of("success", true, "data", product));

        } catch (Exception e) {
            logger.error("Error getting product {} from shop {}: {}", productId, shopId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Cập nhật sản phẩm (upload ảnh mới dưới dạng form-data)
     * PUT /api/seller/shops/{shopId}/products/{productId}
     */
    @PutMapping("/{productId}")
    public ResponseEntity<?> updateProduct(
            @PathVariable Integer shopId,
            @PathVariable Integer productId,
            @RequestParam(value = "data", required = false) String dataJson,
            @RequestParam(value = "images", required = false) MultipartFile[] images,
            @AuthenticationPrincipal User user) {

        logger.info("User {} is updating product {} in shop {}", user.getEmail(), productId, shopId);
        try {
            ProductResponseDTO response = productService.updateProductWithImages(shopId, productId, dataJson, images, user);
            return ResponseEntity.ok(Map.of("success", true, "message", "Product updated successfully", "data", response));
        } catch (Exception e) {
            logger.error("Error updating product {} for shop {}: {}", productId, shopId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<?> deleteProduct(
            @PathVariable Integer shopId,
            @PathVariable Integer productId,
            @AuthenticationPrincipal User user) {

        logger.info("User {} is deleting product {} from shop {}", user.getEmail(), productId, shopId);

        try {
            productService.validateShopOwnership(shopId, user);
            productService.deleteProduct(productId, user);
            return ResponseEntity.ok(Map.of("success", true, "message", "Product deleted successfully"));
        } catch (Exception e) {
            logger.error("Error deleting product {} for shop {}: {}", productId, shopId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/{productId}/variants")
    public ResponseEntity<?> addVariant(
            @PathVariable Integer shopId,
            @PathVariable Integer productId,
            @Valid @RequestBody CreateVariantRequestDTO request,
            @AuthenticationPrincipal User user) {

        logger.info("User {} is adding variant to product {} in shop {}", user.getEmail(), productId, shopId);

        try {
            productService.validateShopOwnership(shopId, user);
            productService.validateProductBelongsToShop(productId, shopId);

            VariantResponseDTO variantResponse = productService.addVariant(productId, request, user);
            return ResponseEntity.ok(Map.of("success", true, "message", "Variant added successfully", "data", variantResponse));
        } catch (Exception e) {
            logger.error("Error adding variant to product {}: {}", productId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PutMapping("/{productId}/variants/{variantId}")
    public ResponseEntity<?> updateVariant(
            @PathVariable Integer shopId,
            @PathVariable Integer productId,
            @PathVariable Integer variantId,
            @Valid @RequestBody UpdateVariantRequestDTO request,
            @AuthenticationPrincipal User user) {

        logger.info("User {} is updating variant {} for product {} in shop {}", user.getEmail(), variantId, productId, shopId);

        try {
            productService.validateShopOwnership(shopId, user);
            productService.validateProductBelongsToShop(productId, shopId);

            VariantResponseDTO variantResponse = productService.updateVariant(variantId, request, user);
            return ResponseEntity.ok(Map.of("success", true, "message", "Variant updated successfully", "data", variantResponse));
        } catch (Exception e) {
            logger.error("Error updating variant {} for product {}: {}", variantId, productId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @DeleteMapping("/{productId}/variants/{variantId}")
    public ResponseEntity<?> deleteVariant(
            @PathVariable Integer shopId,
            @PathVariable Integer productId,
            @PathVariable Integer variantId,
            @AuthenticationPrincipal User user) {

        logger.info("User {} is deleting variant {} from product {} in shop {}", user.getEmail(), variantId, productId, shopId);

        try {
            productService.validateShopOwnership(shopId, user);
            productService.validateProductBelongsToShop(productId, shopId);

            productService.deleteVariant(variantId, user);
            return ResponseEntity.ok(Map.of("success", true, "message", "Variant deleted successfully"));
        } catch (Exception e) {
            logger.error("Error deleting variant {} from product {}: {}", variantId, productId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // Endpoint để admin lấy tất cả sản phẩm (kể cả inactive)
    @GetMapping("/all")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> getAllProducts(@AuthenticationPrincipal User user) {
        logger.info("Admin {} is fetching all products", user.getEmail());

        try {
            List<ProductResponseDTO> products = productService.getAllProducts();
            return ResponseEntity.ok(Map.of("success", true, "data", products));
        } catch (Exception e) {
            logger.error("Error fetching all products: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Failed to fetch products"));
        }
    }
}
