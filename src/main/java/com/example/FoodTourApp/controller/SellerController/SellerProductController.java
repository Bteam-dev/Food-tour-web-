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
import java.util.HashMap;
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
public class SellerProductController {

    private static final Logger logger = LoggerFactory.getLogger(SellerProductController.class);
    private final ProductService productService;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;
    private final ShopRepository shopRepository;

    public SellerProductController(ProductService productService,
                                   FileStorageService fileStorageService,
                                   ObjectMapper objectMapper,
                                   ShopRepository shopRepository) {
        this.productService = productService;
        this.fileStorageService = fileStorageService;
        this.objectMapper = objectMapper;
        this.shopRepository = shopRepository;
    }

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
            // Validate quyền sở hữu shop
            validateShopOwnership(shopId, user);

            // Tạo pageable
            Sort.Direction sortDirection = direction.equalsIgnoreCase("ASC") ? Sort.Direction.ASC : Sort.Direction.DESC;
            Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));

            // Lấy sản phẩm của shop
            Page<ProductResponseDTO> products = productService.getActiveProductsByShop(shopId, pageable);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", products.getContent());
            result.put("currentPage", products.getNumber());
            result.put("totalItems", products.getTotalElements());
            result.put("totalPages", products.getTotalPages());
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("Error getting products for shop {}: {}", shopId, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
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
        logger.info("Received dataJson: {}", dataJson);
        logger.info("Received images count: {}", images != null ? images.length : 0);

        if (images != null) {
            for (int i = 0; i < images.length; i++) {
                logger.info("Image[{}]: name={}, size={}, contentType={}",
                    i, images[i].getOriginalFilename(), images[i].getSize(), images[i].getContentType());
            }
        }

        try {
            // Validate quyền sở hữu shop
            validateShopOwnership(shopId, user);

            // Parse request từ JSON string
            if (dataJson == null || dataJson.isEmpty()) {
                throw new RuntimeException("Thiếu dữ liệu sản phẩm");
            }

            CreateProductRequestDTO request = objectMapper.readValue(dataJson, CreateProductRequestDTO.class);

            // TỰ ĐỘNG gán shopId từ URL path
            request.setShopId(shopId);

            logger.info("Parsed request: name={}, shopId={}, categoryId={}",
                request.getName(), request.getShopId(), request.getCategoryId());

            // Upload nhiều ảnh nếu có - lưu vào thư mục ProductImage/shop_X/
            if (images != null && images.length > 0) {
                logger.info("Starting to upload {} images...", images.length);
                String subfolderId = "shop_" + shopId;
                List<String> imageUrls = fileStorageService.storeFiles(images, FileStorageService.FileCategory.PRODUCT_IMAGE, subfolderId);
                request.setImageUrls(imageUrls);
                logger.info("Uploaded {} images successfully to ProductImage/{}/", imageUrls.size(), subfolderId);
            } else {
                logger.warn("No images received in request!");
            }

            // Lưu product vào database
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
            validateShopOwnership(shopId, user);

            ProductResponseDTO product = productService.getProductById(productId);

            // Kiểm tra sản phẩm có thuộc shop này không
            if (!product.getShopId().equals(shopId)) {
                throw new RuntimeException("Sản phẩm không thuộc shop này");
            }

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", product);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("Error getting product {} from shop {}: {}", productId, shopId, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
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
        logger.info("Received dataJson: {}", dataJson); // ✅ LOG ĐỂ DEBUG

        try {
            validateShopOwnership(shopId, user);

            // Parse request từ JSON string
            if (dataJson == null || dataJson.isEmpty()) {
                throw new RuntimeException("Thiếu dữ liệu cập nhật");
            }

            UpdateProductRequestDTO request = objectMapper.readValue(dataJson, UpdateProductRequestDTO.class);

            // ✅ LOG ĐỂ KIỂM TRA VARIANTS CÓ ĐƯỢC PARSE KHÔNG
            logger.info("Parsed request - variants count: {}",
                    request.getVariants() != null ? request.getVariants().size() : "null");
            if (request.getVariants() != null) {
                request.getVariants().forEach(v ->
                    logger.info("Variant: id={}, variantTypeId={}, value={}, shouldDelete={}",
                            v.getId(), v.getVariantTypeId(), v.getVariantValue(), v.getShouldDelete())
                );
            }

            // Kiểm tra sản phẩm có thuộc shop này không
            ProductResponseDTO product = productService.getProductById(productId);
            if (!product.getShopId().equals(shopId)) {
                throw new RuntimeException("Sản phẩm không thuộc shop này");
            }

            // ✅ Upload ảnh mới vào thư mục: ProductImage/shop_{shopId}/product_{productId}/
            if (images != null && images.length > 0) {
                String subfolderId = "shop_" + shopId + "/product_" + productId;
                List<String> imageUrls = fileStorageService.storeFiles(images, FileStorageService.FileCategory.PRODUCT_IMAGE, subfolderId);
                request.setImageUrls(imageUrls);
                logger.info("Uploaded {} images for product {} to ProductImage/{}/", imageUrls.size(), productId, subfolderId);
            }

            // Lưu cập nhật vào database
            ProductResponseDTO response = productService.updateProduct(productId, request, user);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Product updated successfully");
            result.put("data", response);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("Error updating product {} for user {}: {}", productId, user.getEmail(), e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<?> deleteProduct(
            @PathVariable Integer shopId,
            @PathVariable Integer productId,
            @AuthenticationPrincipal User user) {

        logger.info("User {} is deleting product {} from shop {}", user.getEmail(), productId, shopId);

        try {
            validateShopOwnership(shopId, user);


            productService.deleteProduct(productId, user);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Product deleted successfully");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error deleting product {} for user {}: {}", productId, user.getEmail(), e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
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
            validateShopOwnership(shopId, user);

            // Kiểm tra sản phẩm có thuộc shop này không
            ProductResponseDTO product = productService.getProductById(productId);
            if (!product.getShopId().equals(shopId)) {
                throw new RuntimeException("Sản phẩm không thuộc shop này");
            }

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
    public ResponseEntity<?> updateVariant(
            @PathVariable Integer shopId,
            @PathVariable Integer productId,
            @PathVariable Integer variantId,
            @Valid @RequestBody UpdateVariantRequestDTO request,
            @AuthenticationPrincipal User user) {

        logger.info("User {} is updating variant {} for product {} in shop {}", user.getEmail(), variantId, productId, shopId);

        try {
            validateShopOwnership(shopId, user);

            // Kiểm tra sản phẩm có thuộc shop này không
            ProductResponseDTO product = productService.getProductById(productId);
            if (!product.getShopId().equals(shopId)) {
                throw new RuntimeException("Sản phẩm không thuộc shop này");
            }

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
    public ResponseEntity<?> deleteVariant(
            @PathVariable Integer shopId,
            @PathVariable Integer productId,
            @PathVariable Integer variantId,
            @AuthenticationPrincipal User user) {

        logger.info("User {} is deleting variant {} from product {} in shop {}", user.getEmail(), variantId, productId, shopId);

        try {
            validateShopOwnership(shopId, user);

            // Kiểm tra sản phẩm có thuộc shop này không
            ProductResponseDTO product = productService.getProductById(productId);
            if (!product.getShopId().equals(shopId)) {
                throw new RuntimeException("Sản phẩm không thuộc shop này");
            }

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

    // Endpoint để admin lấy tất cả sản phẩm (kể cả inactive)
    @GetMapping("/all")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> getAllProducts(@AuthenticationPrincipal User user) {
        logger.info("Admin {} is fetching all products", user.getEmail());

        try {
            List<ProductResponseDTO> products = productService.getAllProducts();

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

    /**
     * Helper: Validate quyền sở hữu shop
     */
    private Shop validateShopOwnership(Integer shopId, User user) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new RuntimeException("Shop không tồn tại"));

        // Kiểm tra role ADMIN
        boolean isAdmin = user.getRole() != null &&
                         user.getRole().getRoleName() == Role.RoleName.ADMIN;

        if (!isAdmin && !shop.getSeller().getId().equals(user.getId())) {
            throw new RuntimeException("Bạn không có quyền truy cập shop này");
        }

        return shop;
    }
}
