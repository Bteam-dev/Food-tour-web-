package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.DTO.ProductDTO.CreateProductRequestDTO;
import com.example.FoodTourApp.DTO.ProductDTO.ProductResponseDTO;
import com.example.FoodTourApp.DTO.ProductDTO.UpdateProductRequestDTO;
import com.example.FoodTourApp.DTO.ProductVariantDTO.CreateVariantRequestDTO;
import com.example.FoodTourApp.DTO.ProductVariantDTO.ProductVariantUpdateDTO;
import com.example.FoodTourApp.DTO.ProductVariantDTO.UpdateVariantRequestDTO;
import com.example.FoodTourApp.DTO.ProductVariantDTO.VariantResponseDTO;
import com.example.FoodTourApp.entity.*;
import com.example.FoodTourApp.repository.*;
import com.example.FoodTourApp.service.ProductService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ShopRepository shopRepository;
    private final CategoryRepository categoryRepository;
    private final VariantTypeRepository variantTypeRepository;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;

    // ── Helper: List<String> → JSON string ───────────────────────────────────
    private String toJsonArray(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(list);
        } catch (IOException e) {
            log.error("Failed to serialize list to JSON: {}", e.getMessage());
            return null;
        }
    }

    // ── Helper: JSON string → List<String> ───────────────────────────────────
    private List<String> fromJsonArray(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (IOException e) {
            log.warn("Failed to parse JSON array: {}, raw value: {}", e.getMessage(), json);
            return new ArrayList<>();
        }
    }

    @Override
    @Transactional
    public ProductResponseDTO createProduct(CreateProductRequestDTO request, User seller) {
        log.info("Creating product for seller: {}, shopId: {}", seller.getId(), request.getShopId());

        Shop shop = shopRepository.findById(request.getShopId())
                .orElseThrow(() -> new RuntimeException("Shop not found"));

        if (!shop.getSeller().getId().equals(seller.getId()) && !seller.getRole().getRoleName().equals(Role.RoleName.ADMIN)) {
            throw new RuntimeException("You do not have permission to create product for this shop");
        }

        // Shop phải được admin xác minh trước khi tạo sản phẩm
        if (!Boolean.TRUE.equals(shop.getIsVerified())) {
            throw new RuntimeException("Shop của bạn chưa được admin duyệt. Vui lòng chờ xét duyệt trước khi thêm sản phẩm.");
        }

        // Shop phải đang hoạt động (isActive=true)
        if (!Boolean.TRUE.equals(shop.getIsActive())) {
            throw new RuntimeException("Shop của bạn hiện đang bị vô hiệu hóa. Vui lòng liên hệ admin để được hỗ trợ.");
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new RuntimeException("Category not found"));

        Product product = new Product();
        product.setShop(shop);
        product.setCategory(category);
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());

        if (request.getDiscountPrice() != null) {
            if (request.getDiscountPrice().compareTo(request.getPrice()) >= 0)
                throw new RuntimeException("Giá giảm phải nhỏ hơn giá gốc");
            if (request.getDiscountPrice().compareTo(java.math.BigDecimal.ZERO) < 0)
                throw new RuntimeException("Giá giảm không thể âm");
        }
        product.setDiscountPrice(request.getDiscountPrice());

        // ── imageUrls: lưu JSON array ["url1","url2"] ──────────────────────
        product.setImageUrls(toJsonArray(request.getImageUrls()));

        // ── ingredients: lưu JSON array ["nguyên liệu 1","nguyên liệu 2"] ───
        product.setIngredients(toJsonArray(request.getIngredients()));

        // ── nutritionInfo: lưu JSON array ["calories: 350","protein: 25g"] ───
        product.setNutritionInfo(toJsonArray(request.getNutritionInfo()));

        product.setPreparationTime(request.getPreparationTime());
        product.setStockQuantity(request.getStockQuantity());
        product.setMinOrderQuantity(request.getMinOrderQuantity());
        product.setMaxOrderQuantity(request.getMaxOrderQuantity());

        // ── tags: lưu JSON array ───────────────────────────────────────────
        product.setTags(toJsonArray(request.getTags()));

        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());

        product = productRepository.save(product);

        if (request.getVariants() != null && !request.getVariants().isEmpty()) {
            for (CreateVariantRequestDTO varReq : request.getVariants()) {
                VariantType variantType = variantTypeRepository.findById(varReq.getVariantTypeId())
                        .orElseThrow(() -> new RuntimeException("Variant type not found"));
                ProductVariant variant = new ProductVariant();
                variant.setProduct(product);
                variant.setVariantType(variantType);
                variant.setVariantValue(varReq.getVariantValue());
                variant.setPriceAdjustment(varReq.getPriceAdjustment());
                variantRepository.save(variant);
            }
        }

        log.info("Product created successfully with id: {} for shop: {}", product.getId(), shop.getId());
        return mapToProductResponseDTO(product);
    }

    @Override
    @Transactional
    public ProductResponseDTO updateProduct(Integer productId, UpdateProductRequestDTO request, User user) {
        log.info("Updating product: {} by user: {}", productId, user.getId());

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (!user.getRole().getRoleName().equals(Role.RoleName.ADMIN)) {
            if (!product.getShop().getSeller().getId().equals(user.getId()))
                throw new RuntimeException("You do not have permission to update this product");
        }

        if (request.getName() != null) product.setName(request.getName());
        if (request.getDescription() != null) product.setDescription(request.getDescription());
        if (request.getPrice() != null) product.setPrice(request.getPrice());

        if (request.getDiscountPrice() != null) {
            java.math.BigDecimal priceToCompare = request.getPrice() != null ? request.getPrice() : product.getPrice();
            if (request.getDiscountPrice().compareTo(priceToCompare) >= 0)
                throw new RuntimeException("Giá giảm phải nhỏ hơn giá gốc");
            if (request.getDiscountPrice().compareTo(java.math.BigDecimal.ZERO) < 0)
                throw new RuntimeException("Giá giảm không thể âm");
            product.setDiscountPrice(request.getDiscountPrice());
        }

        // ── imageUrls: lưu JSON array ──────────────────────────────────────
        if (request.getImageUrls() != null) {
            product.setImageUrls(request.getImageUrls().isEmpty() ? null : toJsonArray(request.getImageUrls()));
        }

        // ── ingredients: lưu JSON array ────────────────────────────────────
        if (request.getIngredients() != null) {
            product.setIngredients(request.getIngredients().isEmpty() ? null : toJsonArray(request.getIngredients()));
        }

        // ── nutritionInfo: lưu JSON array ──────────────────────────────────
        if (request.getNutritionInfo() != null) {
            product.setNutritionInfo(request.getNutritionInfo().isEmpty() ? null : toJsonArray(request.getNutritionInfo()));
        }

        if (request.getPreparationTime() != null) product.setPreparationTime(request.getPreparationTime());
        if (request.getIsAvailable() != null) product.setIsAvailable(request.getIsAvailable());
        if (request.getStockQuantity() != null) product.setStockQuantity(request.getStockQuantity());
        if (request.getMinOrderQuantity() != null) product.setMinOrderQuantity(request.getMinOrderQuantity());
        if (request.getMaxOrderQuantity() != null) product.setMaxOrderQuantity(request.getMaxOrderQuantity());

        // ── tags: lưu JSON array ───────────────────────────────────────────
        if (request.getTags() != null) {
            product.setTags(request.getTags().isEmpty() ? null : toJsonArray(request.getTags()));
        }

        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("Category not found"));
            product.setCategory(category);
        }

        product.setUpdatedAt(LocalDateTime.now());
        product = productRepository.save(product);

        if (request.getVariants() != null) {
            log.info("Processing {} variants for product: {}", request.getVariants().size(), productId);
            for (ProductVariantUpdateDTO varUpdate : request.getVariants()) {
                if (varUpdate.getShouldDelete() != null && varUpdate.getShouldDelete()) {
                    if (varUpdate.getId() != null) {
                        ProductVariant variant = variantRepository.findById(varUpdate.getId())
                                .orElseThrow(() -> new RuntimeException("Variant not found with id: " + varUpdate.getId()));
                        if (!user.getRole().getRoleName().equals(Role.RoleName.ADMIN)) {
                            if (!variant.getProduct().getShop().getSeller().getId().equals(user.getId()))
                                throw new RuntimeException("You do not have permission to delete this variant");
                        }
                        variant.setIsActive(false);
                        variantRepository.save(variant);
                        log.info("Variant {} marked as deleted", varUpdate.getId());
                    }
                    continue;
                }
                if (varUpdate.getId() != null) {
                    ProductVariant variant = variantRepository.findById(varUpdate.getId())
                            .orElseThrow(() -> new RuntimeException("Variant not found with id: " + varUpdate.getId()));
                    if (!user.getRole().getRoleName().equals(Role.RoleName.ADMIN)) {
                        if (!variant.getProduct().getShop().getSeller().getId().equals(user.getId()))
                            throw new RuntimeException("You do not have permission to update this variant");
                    }
                    if (varUpdate.getVariantTypeId() != null) {
                        VariantType variantType = variantTypeRepository.findById(varUpdate.getVariantTypeId())
                                .orElseThrow(() -> new RuntimeException("Variant type not found"));
                        variant.setVariantType(variantType);
                    }
                    if (varUpdate.getVariantValue() != null) variant.setVariantValue(varUpdate.getVariantValue());
                    if (varUpdate.getPriceAdjustment() != null) variant.setPriceAdjustment(varUpdate.getPriceAdjustment());
                    if (varUpdate.getIsActive() != null) variant.setIsActive(varUpdate.getIsActive());
                    variantRepository.save(variant);
                    log.info("Variant {} updated successfully", varUpdate.getId());
                } else {
                    if (varUpdate.getVariantTypeId() == null || varUpdate.getVariantValue() == null)
                        throw new RuntimeException("VariantTypeId and VariantValue are required for new variant");
                    VariantType variantType = variantTypeRepository.findById(varUpdate.getVariantTypeId())
                            .orElseThrow(() -> new RuntimeException("Variant type not found"));
                    ProductVariant newVariant = new ProductVariant();
                    newVariant.setProduct(product);
                    newVariant.setVariantType(variantType);
                    newVariant.setVariantValue(varUpdate.getVariantValue());
                    newVariant.setPriceAdjustment(varUpdate.getPriceAdjustment() != null ? varUpdate.getPriceAdjustment() : java.math.BigDecimal.ZERO);
                    newVariant.setIsActive(varUpdate.getIsActive() != null ? varUpdate.getIsActive() : true);
                    variantRepository.save(newVariant);
                    log.info("New variant added: {}", newVariant.getId());
                }
            }
        }

        log.info("Product updated successfully: {}", productId);
        return mapToProductResponseDTO(product);
    }

    @Override
    @Transactional
    public void deleteProduct(Integer productId, User user) {
        log.info("Soft-deleting product: {} by user: {}", productId, user.getId());
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        if (!user.getRole().getRoleName().equals(Role.RoleName.ADMIN)) {
            if (!product.getShop().getSeller().getId().equals(user.getId()))
                throw new RuntimeException("You do not have permission to delete this product");
        }
        product.setIsAvailable(false);
        product.setUpdatedAt(LocalDateTime.now());
        productRepository.save(product);
        List<ProductVariant> variants = variantRepository.findByProductId(productId);
        for (ProductVariant v : variants) {
            v.setIsActive(false);
            variantRepository.save(v);
        }
        log.info("Product soft-deleted (isAvailable=false) successfully: {}", productId);
    }

    @Override
    public ProductResponseDTO getProductById(Integer productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        return mapToProductResponseDTO(product);
    }

    @Override
    public List<ProductResponseDTO> getProductsByShop(Shop shop) {
        return productRepository.findByShop(shop).stream()
                .map(this::mapToProductResponseDTO).collect(Collectors.toList());
    }

    @Override
    public Page<ProductResponseDTO> getActiveProductsByShop(Integer shopId, Pageable pageable) {
        return productRepository.findActiveByShopId(shopId, pageable).map(this::mapToProductResponseDTO);
    }

    @Override
    public Page<ProductResponseDTO> getAllActiveProducts(Pageable pageable) {
        return productRepository.findAllActive(pageable).map(this::mapToProductResponseDTO);
    }

    @Override
    public Page<ProductResponseDTO> getProductsByCategory(Integer categoryId, Pageable pageable) {
        return productRepository.findByCategoryId(categoryId, pageable).map(this::mapToProductResponseDTO);
    }

    @Override
    @Transactional
    public VariantResponseDTO addVariant(Integer productId, CreateVariantRequestDTO request, User user) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        if (!user.getRole().getRoleName().equals(Role.RoleName.ADMIN)) {
            if (!product.getShop().getSeller().getId().equals(user.getId()))
                throw new RuntimeException("You do not have permission to add variant to this product");
        }
        VariantType variantType = variantTypeRepository.findById(request.getVariantTypeId())
                .orElseThrow(() -> new RuntimeException("Variant type not found"));
        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setVariantType(variantType);
        variant.setVariantValue(request.getVariantValue());
        variant.setPriceAdjustment(request.getPriceAdjustment());
        variant = variantRepository.save(variant);
        log.info("Variant added successfully: {}", variant.getId());
        return mapToVariantResponseDTO(variant);
    }

    @Override
    @Transactional
    public VariantResponseDTO updateVariant(Integer variantId, UpdateVariantRequestDTO request, User user) {
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new RuntimeException("Variant not found"));
        if (!user.getRole().getRoleName().equals(Role.RoleName.ADMIN)) {
            if (!variant.getProduct().getShop().getSeller().getId().equals(user.getId()))
                throw new RuntimeException("You do not have permission to update this variant");
        }
        if (request.getVariantTypeId() != null) {
            VariantType variantType = variantTypeRepository.findById(request.getVariantTypeId())
                    .orElseThrow(() -> new RuntimeException("Variant type not found"));
            variant.setVariantType(variantType);
        }
        if (request.getVariantValue() != null) variant.setVariantValue(request.getVariantValue());
        if (request.getPriceAdjustment() != null) variant.setPriceAdjustment(request.getPriceAdjustment());
        if (request.getIsActive() != null) variant.setIsActive(request.getIsActive());
        variant = variantRepository.save(variant);
        log.info("Variant updated successfully: {}", variantId);
        return mapToVariantResponseDTO(variant);
    }

    @Override
    @Transactional
    public void deleteVariant(Integer variantId, User user) {
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new RuntimeException("Variant not found"));
        if (!user.getRole().getRoleName().equals(Role.RoleName.ADMIN)) {
            if (!variant.getProduct().getShop().getSeller().getId().equals(user.getId()))
                throw new RuntimeException("You do not have permission to delete this variant");
        }
        variant.setIsActive(false);
        variantRepository.save(variant);
        log.info("Variant deleted (soft) successfully: {}", variantId);
    }

    @Override
    public List<ProductResponseDTO> getAllProducts() {
        return productRepository.findAll().stream()
                .map(this::mapToProductResponseDTO).collect(Collectors.toList());
    }

    @Override
    public void validateShopOwnership(Integer shopId, User user) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new RuntimeException("Shop không tồn tại"));
        boolean isAdmin = user.getRole() != null && user.getRole().getRoleName() == Role.RoleName.ADMIN;
        if (!isAdmin && !shop.getSeller().getId().equals(user.getId()))
            throw new RuntimeException("Bạn không có quyền truy cập shop này");
    }

    @Override
    public void validateProductBelongsToShop(Integer productId, Integer shopId) {
        ProductResponseDTO product = getProductById(productId);
        if (!product.getShopId().equals(shopId))
            throw new RuntimeException("Sản phẩm không thuộc shop này");
    }

    // ── Map entity → DTO ─────────────────────────────────────────────────────
    private ProductResponseDTO mapToProductResponseDTO(Product product) {
        ProductResponseDTO dto = new ProductResponseDTO();
        dto.setId(product.getId());

        Shop shop = product.getShop();
        dto.setShopId(shop.getId());
        dto.setShopName(shop.getShopName());
        
        // ✅ NEW: Calculate if shop is currently open based on openingHours
        dto.setShopIsOpen(isShopCurrentlyOpen(shop));
        
        dto.setCategoryId(product.getCategory().getId());
        dto.setCategoryName(product.getCategory().getName());
        dto.setName(product.getName());
        dto.setDescription(product.getDescription());
        dto.setPrice(product.getPrice());
        dto.setDiscountPrice(product.getDiscountPrice());

        // ── imageUrls: parse từ JSON array ────────────────────────────────
        List<String> imageUrls = fromJsonArray(product.getImageUrls());
        dto.setImageUrls(imageUrls.isEmpty() ? null : imageUrls);

        // ── ingredients: parse từ JSON array ───────────────────────────────
        List<String> ingredients = fromJsonArray(product.getIngredients());
        dto.setIngredients(ingredients.isEmpty() ? null : ingredients);

        // ── nutritionInfo: parse từ JSON array ─────────────────────────────
        List<String> nutritionInfo = fromJsonArray(product.getNutritionInfo());
        dto.setNutritionInfo(nutritionInfo.isEmpty() ? null : nutritionInfo);

        dto.setPreparationTime(product.getPreparationTime());
        dto.setIsAvailable(product.getIsAvailable());
        dto.setStockQuantity(product.getStockQuantity());
        dto.setMinOrderQuantity(product.getMinOrderQuantity());
        dto.setMaxOrderQuantity(product.getMaxOrderQuantity());
        dto.setRating(product.getRating());
        dto.setTotalReviews(product.getTotalReviews());

        // ── tags: parse từ JSON array ─────────────────────────────────────
        List<String> tags = fromJsonArray(product.getTags());
        dto.setTags(tags.isEmpty() ? null : tags);

        dto.setCreatedAt(product.getCreatedAt());
        dto.setUpdatedAt(product.getUpdatedAt());

        List<ProductVariant> variants = variantRepository.findActiveByProduct(product);
        dto.setVariants(variants.stream().map(this::mapToVariantResponseDTO).collect(Collectors.toList()));

        return dto;
    }
    
    /**
     * ✅ NEW: Check if shop is currently open based on openingHours JSON
     * Format: {"MONDAY":{"open":"09:00","close":"22:00"}, ...}
     */
    private boolean isShopCurrentlyOpen(Shop shop) {
        String openingHoursJson = shop.getOpeningHours();
        if (openingHoursJson == null || openingHoursJson.isBlank()) {
            return true; // No hours specified = always open
        }
        
        try {
            LocalDateTime now = LocalDateTime.now();
            DayOfWeek today = now.getDayOfWeek();
            LocalTime currentTime = now.toLocalTime();
            
            // Parse opening hours JSON
            var hoursMap = objectMapper.readValue(openingHoursJson, 
                new TypeReference<java.util.Map<String, java.util.Map<String, String>>>() {});
            
            var todayHours = hoursMap.get(today.name().toLowerCase());
            if (todayHours == null) {
                return false; // No hours for today = closed
            }
            
            String openStr = todayHours.get("open");
            String closeStr = todayHours.get("close");
            
            if (openStr == null || closeStr == null) {
                return false;
            }
            
            LocalTime openTime = LocalTime.parse(openStr);
            LocalTime closeTime = LocalTime.parse(closeStr);
            
            // Handle overnight hours (e.g., 22:00 - 02:00)
            if (closeTime.isBefore(openTime)) {
                return currentTime.isAfter(openTime) || currentTime.isBefore(closeTime);
            }
            
            return !currentTime.isBefore(openTime) && !currentTime.isAfter(closeTime);
            
        } catch (Exception e) {
            log.warn("Failed to parse shop opening hours for shop {}: {}", shop.getId(), e.getMessage());
            return true; // Fallback to open if parse fails
        }
    }

    private VariantResponseDTO mapToVariantResponseDTO(ProductVariant variant) {
        VariantResponseDTO dto = new VariantResponseDTO();
        dto.setId(variant.getId());
        dto.setVariantTypeId(variant.getVariantType().getId());
        dto.setVariantTypeName(variant.getVariantType().getName());
        dto.setVariantValue(variant.getVariantValue());
        dto.setPriceAdjustment(variant.getPriceAdjustment());
        dto.setIsActive(variant.getIsActive());
        return dto;
    }

    @Override
    @Transactional
    public ProductResponseDTO createProductWithImages(Integer shopId, String dataJson, MultipartFile[] images, User user) {
        if (dataJson == null || dataJson.isEmpty()) throw new RuntimeException("Thiếu dữ liệu sản phẩm");
        validateShopOwnership(shopId, user);
        CreateProductRequestDTO request;
        try {
            request = objectMapper.readValue(dataJson, CreateProductRequestDTO.class);
        } catch (Exception e) {
            throw new RuntimeException("Dữ liệu sản phẩm không hợp lệ: " + e.getMessage(), e);
        }
        request.setShopId(shopId);
        if (images != null && images.length > 0) {
            try {
                // Temporary product ID will be available after save, so we'll store images after creation
                // For now, we'll create product first, then update with images
                ProductResponseDTO productResponse = createProduct(request, user);
                
                // Now store images with full hierarchy: user_id/shop_id/product_id
                String hierarchyPath = "user_" + user.getId() + "/shop_" + shopId + "/product_" + productResponse.getId();
                List<String> imageUrls = fileStorageService.storeFilesWithHierarchy(
                    images, 
                    FileStorageService.FileCategory.PRODUCT_IMAGE, 
                    hierarchyPath
                );
                
                // Update product with image URLs
                Product product = productRepository.findById(productResponse.getId())
                    .orElseThrow(() -> new RuntimeException("Product not found after creation"));
                product.setImageUrls(toJsonArray(imageUrls));
                productRepository.save(product);
                
                productResponse.setImageUrls(imageUrls);
                log.info("Uploaded {} images for product {} in shop {} with hierarchy: {}", 
                    imageUrls.size(), productResponse.getId(), shopId, hierarchyPath);
                return productResponse;
            } catch (java.io.IOException e) {
                throw new RuntimeException("Không thể upload ảnh sản phẩm: " + e.getMessage(), e);
            }
        }
        return createProduct(request, user);
    }

    @Override
    @Transactional
    public ProductResponseDTO updateProductWithImages(Integer shopId, Integer productId, String dataJson, MultipartFile[] images, User user) {
        if (dataJson == null || dataJson.isEmpty()) throw new RuntimeException("Thiếu dữ liệu cập nhật");
        validateShopOwnership(shopId, user);
        validateProductBelongsToShop(productId, shopId);
        UpdateProductRequestDTO request;
        try {
            request = objectMapper.readValue(dataJson, UpdateProductRequestDTO.class);
        } catch (Exception e) {
            throw new RuntimeException("Dữ liệu cập nhật không hợp lệ: " + e.getMessage(), e);
        }
        
        // Delete old images if new images are being uploaded
        if (images != null && images.length > 0) {
            try {
                Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new RuntimeException("Product not found"));
                
                // Delete old images
                List<String> oldImageUrls = fromJsonArray(product.getImageUrls());
                if (!oldImageUrls.isEmpty()) {
                    log.info("Deleting {} old images for product {}", oldImageUrls.size(), productId);
                    fileStorageService.deleteFiles(oldImageUrls);
                }
                
                // Upload new images with hierarchy: user_id/shop_id/product_id
                String hierarchyPath = "user_" + user.getId() + "/shop_" + shopId + "/product_" + productId;
                List<String> imageUrls = fileStorageService.storeFilesWithHierarchy(
                    images, 
                    FileStorageService.FileCategory.PRODUCT_IMAGE, 
                    hierarchyPath
                );
                request.setImageUrls(imageUrls);
                log.info("Uploaded {} new images for product {} with hierarchy: {}", 
                    imageUrls.size(), productId, hierarchyPath);
            } catch (java.io.IOException e) {
                throw new RuntimeException("Không thể upload ảnh sản phẩm: " + e.getMessage(), e);
            }
        }
        return updateProduct(productId, request, user);
    }

    @Override
    public Page<ProductResponseDTO> getProductsByNameContaining(String keyword, Pageable pageable) {
        if (keyword == null || keyword.trim().isEmpty())
            return productRepository.findAllActive(pageable).map(this::mapToProductResponseDTO);
        return productRepository.findByNameContainingIgnoreCase(keyword, pageable)
                .map(this::mapToProductResponseDTO);
    }

    /**
     * Lọc và sắp xếp sản phẩm nâng cao.
     *
     * sortBy:
     *   "rating_desc"   → rating cao → thấp (dùng Pageable + Sort)
     *   "rating_asc"    → rating thấp → cao
     *   "best_selling"  → bán chạy nhất (query riêng)
     *   "newest"        → mới nhất (createdAt DESC)
     *   "price_asc"     → giá thấp → cao
     *   "price_desc"    → giá cao → thấp
     *   null / ""       → mặc định createdAt DESC
     */
    @Override
    public Page<ProductResponseDTO> getFilteredProducts(String sortBy, String city, Integer categoryId,
                                                         String keyword, java.math.BigDecimal minPrice, java.math.BigDecimal maxPrice,
                                                         Pageable pageable) {
        boolean isBestSelling = "best_selling".equalsIgnoreCase(sortBy);

        // Chuẩn hóa params
        String normalizedKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        String normalizedCity    = (city    != null && !city.isBlank())    ? city.trim()    : null;

        if (!isBestSelling) {
            org.springframework.data.domain.Sort sort = switch (sortBy == null ? "" : sortBy.toLowerCase()) {
                case "rating_desc" -> org.springframework.data.domain.Sort.by("rating").descending();
                case "rating_asc"  -> org.springframework.data.domain.Sort.by("rating").ascending();
                case "price_asc"   -> org.springframework.data.domain.Sort.by("price").ascending();
                case "price_desc"  -> org.springframework.data.domain.Sort.by("price").descending();
                default            -> org.springframework.data.domain.Sort.by("createdAt").descending();
            };
            pageable = org.springframework.data.domain.PageRequest.of(
                    pageable.getPageNumber(), pageable.getPageSize(), sort);
        }

        // Nếu có bất kỳ filter nâng cao (keyword / price range) → dùng query tổng hợp
        boolean hasAdvancedFilter = normalizedKeyword != null || minPrice != null || maxPrice != null;

        if (isBestSelling) {
            if (hasAdvancedFilter) {
                return productRepository.findBestSellingWithFilters(
                        normalizedKeyword, minPrice, maxPrice, categoryId, normalizedCity, pageable)
                        .map(this::mapToProductResponseDTO);
            }
            if (normalizedCity != null) {
                return productRepository.findBestSellingByCity(normalizedCity, pageable).map(this::mapToProductResponseDTO);
            }
            return productRepository.findBestSelling(pageable).map(this::mapToProductResponseDTO);
        }

        if (hasAdvancedFilter) {
            return productRepository.findWithFilters(
                    normalizedKeyword, minPrice, maxPrice, categoryId, normalizedCity, pageable)
                    .map(this::mapToProductResponseDTO);
        }

        // Legacy path (không có keyword/price) — giữ nguyên logic cũ
        if (normalizedCity != null && categoryId != null) {
            return productRepository.findAvailableByCityAndCategory(normalizedCity, categoryId, pageable).map(this::mapToProductResponseDTO);
        }
        if (normalizedCity != null) {
            return productRepository.findAvailableByCity(normalizedCity, pageable).map(this::mapToProductResponseDTO);
        }
        if (categoryId != null) {
            return productRepository.findByCategoryId(categoryId, pageable).map(this::mapToProductResponseDTO);
        }
        return productRepository.findAllActive(pageable).map(this::mapToProductResponseDTO);
    }
}
