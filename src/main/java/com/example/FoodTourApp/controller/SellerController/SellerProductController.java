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
import com.example.FoodTourApp.service.impl.FileStorageService;
import com.example.FoodTourApp.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/seller/products")
@PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
public class SellerProductController {

    private static final Logger logger = LoggerFactory.getLogger(SellerProductController.class);
    private final ProductService productService;
    private final ShopRepository shopRepository;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;

    public SellerProductController(ProductService productService, ShopRepository shopRepository,
                                   FileStorageService fileStorageService, ObjectMapper objectMapper) {
        this.productService = productService;
        this.shopRepository = shopRepository;
        this.fileStorageService = fileStorageService;
        this.objectMapper = objectMapper;
    }

    /**
     * Tạo sản phẩm mới (có thể upload nhiều ảnh đồ ăn từ máy)
     * POST /api/seller/products
     *
     * Cách sử dụng giống như đăng bài trên Facebook:
     * - Nếu KHÔNG có ảnh: gửi application/json với data thông thường
     * - Nếu CÓ ảnh: gửi multipart/form-data với data + nhiều file ảnh
     *
     * Form data (khi có ảnh):
     * - data: JSON string (CreateProductRequestDTO)
     * - images: multiple files (có thể chọn nhiều ảnh cùng lúc, jpg/jpeg/png, max 5MB each)
     */
    @PostMapping
    public ResponseEntity<?> createProduct(
            @RequestParam(value = "data", required = false) String dataJson,
            @RequestParam(value = "images", required = false) MultipartFile[] images,
            @AuthenticationPrincipal User user) {

        logger.info("User {} is creating a product", user.getEmail());
        logger.info("Received dataJson: {}", dataJson);
        logger.info("Received images count: {}", images != null ? images.length : 0);

        if (images != null) {
            for (int i = 0; i < images.length; i++) {
                logger.info("Image[{}]: name={}, size={}, contentType={}",
                    i, images[i].getOriginalFilename(), images[i].getSize(), images[i].getContentType());
            }
        }

        try {
            // Parse request từ JSON string
            if (dataJson == null || dataJson.isEmpty()) {
                throw new RuntimeException("Thiếu dữ liệu sản phẩm");
            }

            CreateProductRequestDTO request = objectMapper.readValue(dataJson, CreateProductRequestDTO.class);

            logger.info("Parsed request: name={}, shopId={}, categoryId={}",
                request.getName(), request.getShopId(), request.getCategoryId());

            // Upload nhiều ảnh nếu có (giống như chọn nhiều ảnh khi đăng bài Facebook)
            if (images != null && images.length > 0) {
                logger.info("Starting to upload {} images...", images.length);
                List<String> imageUrls = fileStorageService.storeFiles(images, "products/" + user.getId());
                request.setImageUrls(imageUrls);
                logger.info("Uploaded {} images successfully. URLs: {}", imageUrls.size(), imageUrls);
            } else {
                logger.warn("No images received in request!");
            }

            // Lưu product vào database (đã có list URL ảnh nếu user đã upload)
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

    /**
     * Cập nhật sản phẩm (có thể upload nhiều ảnh mới từ máy)
     * PUT /api/seller/products/{id}
     *
     * Cách sử dụng giống như chỉnh sửa bài đăng trên Facebook:
     * - Nếu KHÔNG đổi ảnh: gửi application/json với data thông thường
     * - Nếu CÓ đổi ảnh: gửi multipart/form-data với data + file ảnh mới
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateProduct(
            @PathVariable Integer id,
            @RequestParam(value = "data", required = false) String dataJson,
            @RequestParam(value = "images", required = false) MultipartFile[] images,
            @AuthenticationPrincipal User user) {

        logger.info("User {} is updating product {}", user.getEmail(), id);

        try {
            // Parse request từ JSON string
            if (dataJson == null || dataJson.isEmpty()) {
                throw new RuntimeException("Thiếu dữ liệu cập nhật");
            }

            UpdateProductRequestDTO request = objectMapper.readValue(dataJson, UpdateProductRequestDTO.class);

            // Upload nhiều ảnh mới nếu có (sẽ thay thế ảnh cũ)
            if (images != null && images.length > 0) {
                List<String> imageUrls = fileStorageService.storeFiles(images, "products/" + user.getId());
                request.setImageUrls(imageUrls);
                logger.info("Uploaded {} images for product {}", imageUrls.size(), id);
            }

            // Lưu cập nhật vào database
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

    @PostMapping("/{productId}/variants")
    public ResponseEntity<?> addVariant(@PathVariable Integer productId,
                                        @Valid @RequestBody CreateVariantRequestDTO request,
                                        @AuthenticationPrincipal User user) {
        logger.info("User {} is adding variant to product {}", user.getEmail(), productId);

        try {
            // Kiểm tra quyền: chỉ seller sở hữu sản phẩm hoặc admin mới được phép thêm variant
            ProductResponseDTO product = productService.getProductById(productId);
            Shop shop = shopRepository.findById(product.getShopId())
                    .orElseThrow(() -> new RuntimeException("Shop not found"));

            if (!shop.getSeller().getId().equals(user.getId()) &&
                !user.getRole().getRoleName().equals(Role.RoleName.ADMIN)) {
                throw new RuntimeException("You do not have permission to add variant to this product");
            }

            // Thêm variant mới
            VariantResponseDTO variantResponse = productService.addVariant(productId, request, user);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Variant added successfully");
            result.put("data", variantResponse);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error adding variant to product {} for user {}: {}", productId, user.getEmail(), e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PutMapping("/{productId}/variants/{variantId}")
    public ResponseEntity<?> updateVariant(@PathVariable Integer productId,
                                           @PathVariable Integer variantId,
                                           @Valid @RequestBody UpdateVariantRequestDTO request,
                                           @AuthenticationPrincipal User user) {
        logger.info("User {} is updating variant {} for product {}", user.getEmail(), variantId, productId);

        try {
            // Kiểm tra quyền: chỉ seller sở hữu sản phẩm hoặc admin mới được phép sửa variant
            ProductResponseDTO product = productService.getProductById(productId);
            Shop shop = shopRepository.findById(product.getShopId())
                    .orElseThrow(() -> new RuntimeException("Shop not found"));

            if (!shop.getSeller().getId().equals(user.getId()) &&
                !user.getRole().getRoleName().equals(Role.RoleName.ADMIN)) {
                throw new RuntimeException("You do not have permission to update variant of this product");
            }

            // Cập nhật variant
            VariantResponseDTO variantResponse = productService.updateVariant(variantId, request, user);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Variant updated successfully");
            result.put("data", variantResponse);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error updating variant {} for product {}: {}", variantId, productId, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @DeleteMapping("/{productId}/variants/{variantId}")
    public ResponseEntity<?> deleteVariant(@PathVariable Integer productId,
                                           @PathVariable Integer variantId,
                                           @AuthenticationPrincipal User user) {
        logger.info("User {} is deleting variant {} from product {}", user.getEmail(), variantId, productId);

        try {
            // Kiểm tra quyền: chỉ seller sở hữu sản phẩm hoặc admin mới được phép xóa variant
            ProductResponseDTO product = productService.getProductById(productId);
            Shop shop = shopRepository.findById(product.getShopId())
                    .orElseThrow(() -> new RuntimeException("Shop not found"));

            if (!shop.getSeller().getId().equals(user.getId()) &&
                !user.getRole().getRoleName().equals(Role.RoleName.ADMIN)) {
                throw new RuntimeException("You do not have permission to delete variant from this product");
            }

            // Xóa variant
            productService.deleteVariant(variantId, user);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Variant deleted successfully");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error deleting variant {} from product {}: {}", variantId, productId, e.getMessage(), e);
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

