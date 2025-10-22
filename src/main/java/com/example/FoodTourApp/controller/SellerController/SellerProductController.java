package com.example.FoodTourApp.controller.SellerController;

import com.example.FoodTourApp.DTO.ProductDTO.CreateProductRequestDTO;
import com.example.FoodTourApp.DTO.ProductDTO.ProductResponseDTO;
import com.example.FoodTourApp.DTO.ProductDTO.UpdateProductRequestDTO;
import com.example.FoodTourApp.DTO.ProductVariantDTO.CreateVariantRequestDTO;
import com.example.FoodTourApp.DTO.ProductVariantDTO.UpdateVariantRequestDTO;
import com.example.FoodTourApp.DTO.ProductVariantDTO.VariantResponseDTO;
import com.example.FoodTourApp.entity.Role;
import com.example.FoodTourApp.entity.Shop;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.repository.ShopRepository;
import com.example.FoodTourApp.service.ProductService;
import org.apache.tomcat.util.net.openssl.ciphers.Authentication;
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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/seller/products")
@PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
public class SellerProductController {

    private static final Logger logger = LoggerFactory.getLogger(SellerProductController.class);
    private final ProductService productService;
    private final ShopRepository shopRepository;

    public SellerProductController(ProductService productService, ShopRepository shopRepository) {
        this.productService = productService;
        this.shopRepository = shopRepository;
    }

    @PostMapping
    public ResponseEntity<?> createProduct(@Valid @RequestBody CreateProductRequestDTO request,
                                           @AuthenticationPrincipal User user) {
        logger.info("User {} is creating a product for shopId: {}", user.getEmail(), request.getShopId());

        try {
            ProductResponseDTO response = productService.createProduct(request, user);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Product created successfully");
            result.put("data", response);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error creating product for user {}: {}", user.getEmail(), e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateProduct(@PathVariable Integer id,
                                           @Valid @RequestBody UpdateProductRequestDTO request,
                                           @AuthenticationPrincipal User user) {
        logger.info("User {} is updating product {}", user.getEmail(), id);

        try {
            ProductResponseDTO response = productService.updateProduct(id, request, user);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Product updated successfully");
            result.put("data", response);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error updating product {} for user {}: {}", id, user.getEmail(), e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteProduct(@PathVariable Integer id,
                                           @AuthenticationPrincipal User user) {
        logger.info("User {} is deleting product {}", user.getEmail(), id);

        try {
            productService.deleteProduct(id, user);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Product deleted successfully");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error deleting product {} for user {}: {}", id, user.getEmail(), e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/my-shop")
    public ResponseEntity<?> getMyShopProducts(@PathVariable(required = false) Integer shopId,
                                               @AuthenticationPrincipal User user) {
        logger.info("User {} is fetching products for shopId: {}", user.getEmail(), shopId);

        try {
            List<Shop> shops;
            if (shopId != null) {
                Shop shop = shopRepository.findById(shopId)
                        .orElseThrow(() -> new RuntimeException("Shop not found"));
                if (!shop.getSeller().getId().equals(user.getId()) && !user.getRole().getRoleName().equals(Role.RoleName.ADMIN)) {
                    throw new RuntimeException("You do not have permission to access this shop");
                }
                shops = List.of(shop);
            } else {
                shops = shopRepository.findBySeller(user);
                if (shops.isEmpty() && !user.getRole().getRoleName().equals(Role.RoleName.ADMIN)) {
                    throw new RuntimeException("No shop found for seller");
                }
            }

            List<ProductResponseDTO> products = shops.stream()
                    .flatMap(shop -> productService.getProductsByShop(shop).stream())
                    .collect(Collectors.toList());

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", products);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error fetching shop products for user {}: {}", user.getEmail(), e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/{productId}/variants")
    public ResponseEntity<?> addVariant(@PathVariable Integer productId,
                                        @Valid @RequestBody CreateVariantRequestDTO request,
                                        @AuthenticationPrincipal User user) {
        logger.info("User {} is adding variant to product {}", user.getEmail(), productId);

        try {
            VariantResponseDTO response = productService.addVariant(productId, request, user);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Variant added successfully");
            result.put("data", response);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error adding variant to product {} for user {}: {}", productId, user.getEmail(), e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PutMapping("/variants/{variantId}")
    public ResponseEntity<?> updateVariant(@PathVariable Integer variantId,
                                           @Valid @RequestBody UpdateVariantRequestDTO request,
                                           @AuthenticationPrincipal User user) {
        logger.info("User {} is updating variant {}", user.getEmail(), variantId);

        try {
            VariantResponseDTO response = productService.updateVariant(variantId, request, user);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Variant updated successfully");
            result.put("data", response);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error updating variant {} for user {}: {}", variantId, user.getEmail(), e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @DeleteMapping("/variants/{variantId}")
    public ResponseEntity<?> deleteVariant(@PathVariable Integer variantId,
                                           @AuthenticationPrincipal User user) {
        logger.info("User {} is deleting variant {}", user.getEmail(), variantId);

        try {
            productService.deleteVariant(variantId, user);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Variant deleted successfully");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error deleting variant {} for user {}: {}", variantId, user.getEmail(), e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/my-shops")
    public ResponseEntity<?> getMyShops(@AuthenticationPrincipal User user) {
        logger.info("User {} is fetching their shops", user.getEmail());

        try {
            List<Shop> shops = shopRepository.findBySeller(user);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", shops.stream().map(shop -> new HashMap<String, Object>() {{
                put("id", shop.getId());
                put("shopName", shop.getShopName());
            }}).collect(Collectors.toList()));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error fetching shops for user {}: {}", user.getEmail(), e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    // Endpoint mới để admin lấy tất cả sản phẩm (kể cả inactive)
    @GetMapping("/all")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> getAllProducts(@AuthenticationPrincipal User user) {
        logger.info("Admin {} is fetching all products", user.getEmail());

        try {
            List<ProductResponseDTO> products = productService.getAllProducts(); // Giả sử có method này trong ProductService

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", products);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error fetching all products for admin {}: {}", user.getEmail(), e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to fetch products");
            return ResponseEntity.internalServerError().body(error);
        }
    }
}