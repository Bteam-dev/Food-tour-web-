package com.example.FoodTourApp.controller.SellerController;

import com.example.FoodTourApp.DTO.ProductDTO.CreateProductRequestDTO;
import com.example.FoodTourApp.DTO.ProductDTO.ProductResponseDTO;
import com.example.FoodTourApp.DTO.ProductDTO.UpdateProductRequestDTO;
import com.example.FoodTourApp.DTO.ProductVariantDTO.CreateVariantRequestDTO;
import com.example.FoodTourApp.DTO.ProductVariantDTO.UpdateVariantRequestDTO;
import com.example.FoodTourApp.DTO.ProductVariantDTO.VariantResponseDTO;
import com.example.FoodTourApp.entity.User;
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
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;

    public SellerProductController(ProductService productService,
                                   FileStorageService fileStorageService, ObjectMapper objectMapper) {
        this.productService = productService;
        this.fileStorageService = fileStorageService;
        this.objectMapper = objectMapper;
    }

    /**
     * Tạo sản phẩm mới (upload ảnh sản phẩm dưới dạng form-data)
     * POST /api/seller/products
     *
     * Cách sử dụng:
     * - Content-Type: multipart/form-data
     * - data: JSON string (CreateProductRequestDTO) - REQUIRED
     * - images: multiple files (có thể chọn nhiều ảnh, jpg/jpeg/png, max 5MB each)
     *
     * NOTE: Không cần gửi imageUrls trong JSON nữa, chỉ cần upload file
     * NOTE 2: Ảnh sẽ được lưu vào ProductImage/shop_X/ để phân biệt theo shop
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

            // Upload nhiều ảnh nếu có - lưu vào thư mục ProductImage/shop_X/
            if (images != null && images.length > 0) {
                logger.info("Starting to upload {} images...", images.length);
                // Tạo subfolder ID dựa trên shop ID - để phân biệt ảnh của từng shop
                String subfolderId = "shop_" + request.getShopId();
                List<String> imageUrls = fileStorageService.storeFiles(images, FileStorageService.FileCategory.PRODUCT_IMAGE, subfolderId);
                request.setImageUrls(imageUrls);
                logger.info("Uploaded {} images successfully to ProductImage/{}/", imageUrls.size(), subfolderId);
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
     * Cập nhật sản phẩm (upload ảnh mới dưới dạng form-data)
     * PUT /api/seller/products/{id}
     *
     * Cách sử dụng:
     * - Content-Type: multipart/form-data
     * - data: JSON string (UpdateProductRequestDTO) - REQUIRED
     * - images: multiple files (optional, nếu muốn đổi ảnh mới)
     *
     * NOTE: Ảnh sẽ THAY THẾ ảnh cũ và lưu vào ProductImage/shop_X/ (cùng folder với ảnh tạo mới)
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

            // Upload nhiều ảnh mới nếu có - lưu vào thư mục ProductImage/shop_X/ (cùng folder với ảnh tạo)
            if (images != null && images.length > 0) {
                // Lấy thông tin product để biết shopId
                ProductResponseDTO product = productService.getProductById(id);

                // Tạo subfolder ID dựa trên shop ID - để tất cả ảnh của shop ở cùng 1 folder
                String subfolderId = "shop_" + product.getShopId();
                List<String> imageUrls = fileStorageService.storeFiles(images, FileStorageService.FileCategory.PRODUCT_IMAGE, subfolderId);
                request.setImageUrls(imageUrls);
                logger.info("Uploaded {} images for product {} to ProductImage/{}/", imageUrls.size(), id, subfolderId);
            }

            // Lưu cập nhật vào database (sẽ xóa ảnh cũ nếu có ảnh mới)
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
            // Thêm variant mới - kiểm tra quyền sở hữu được xử lý trong service layer
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
            // Cập nhật variant - kiểm tra quyền sở hữu được xử lý trong service layer
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
            // Xóa variant - kiểm tra quyền sở hữu được xử lý trong service layer
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

