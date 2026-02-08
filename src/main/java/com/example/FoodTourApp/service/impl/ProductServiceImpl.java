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
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
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
    private final CartItemRepository cartItemRepository;
    private final WishlistRepository wishlistRepository;

    @Override
    @Transactional
    public ProductResponseDTO createProduct(CreateProductRequestDTO request, User seller) {
        log.info("Creating product for seller: {}, shopId: {}", seller.getId(), request.getShopId());

        Shop shop = shopRepository.findById(request.getShopId())
                .orElseThrow(() -> new RuntimeException("Shop not found"));

        if (!shop.getSeller().getId().equals(seller.getId()) && !seller.getRole().getRoleName().equals(Role.RoleName.ADMIN)) {
            throw new RuntimeException("You do not have permission to create product for this shop");
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new RuntimeException("Category not found"));

        Product product = new Product();
        product.setShop(shop);
        product.setCategory(category);
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setDiscountPrice(request.getDiscountPrice());

        // Fix: Xử lý imageUrls - chỉ set khi có giá trị
        if (request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
            product.setImageUrls(String.join(",", request.getImageUrls()));
        } else {
            product.setImageUrls(null);
        }

        product.setIngredients(request.getIngredients());
        product.setNutritionInfo(request.getNutritionInfo());
        product.setPreparationTime(request.getPreparationTime());
        product.setStockQuantity(request.getStockQuantity());
        product.setMinOrderQuantity(request.getMinOrderQuantity());
        product.setMaxOrderQuantity(request.getMaxOrderQuantity());

        // Fix: Xử lý tags - chỉ set khi có giá trị
        if (request.getTags() != null && !request.getTags().isEmpty()) {
            product.setTags(String.join(",", request.getTags()));
        } else {
            product.setTags(null);
        }

        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());

        // ✅ LƯU PRODUCT TRƯỚC để có ID
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
            if (!product.getShop().getSeller().getId().equals(user.getId())) {
                throw new RuntimeException("You do not have permission to update this product");
            }
        }

        if (request.getName() != null) product.setName(request.getName());
        if (request.getDescription() != null) product.setDescription(request.getDescription());
        if (request.getPrice() != null) product.setPrice(request.getPrice());
        if (request.getDiscountPrice() != null) product.setDiscountPrice(request.getDiscountPrice());

        // Fix: Xử lý imageUrls - chỉ set khi có giá trị
        if (request.getImageUrls() != null) {
            if (!request.getImageUrls().isEmpty()) {
                product.setImageUrls(String.join(",", request.getImageUrls()));
            } else {
                product.setImageUrls(null);
            }
        }

        if (request.getIngredients() != null) product.setIngredients(request.getIngredients());
        if (request.getNutritionInfo() != null) product.setNutritionInfo(request.getNutritionInfo());
        if (request.getPreparationTime() != null) product.setPreparationTime(request.getPreparationTime());
        if (request.getIsAvailable() != null) product.setIsAvailable(request.getIsAvailable());
        if (request.getStockQuantity() != null) product.setStockQuantity(request.getStockQuantity());
        if (request.getMinOrderQuantity() != null) product.setMinOrderQuantity(request.getMinOrderQuantity());
        if (request.getMaxOrderQuantity() != null) product.setMaxOrderQuantity(request.getMaxOrderQuantity());

        // Fix: Xử lý tags - chỉ set khi có giá trị
        if (request.getTags() != null) {
            if (!request.getTags().isEmpty()) {
                product.setTags(String.join(",", request.getTags()));
            } else {
                product.setTags(null);
            }
        }

        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("Category not found"));
            product.setCategory(category);
        }

        product.setUpdatedAt(LocalDateTime.now());
        product = productRepository.save(product);

        // ✅ XỬ LÝ CẬP NHẬT VARIANTS (THÊM MỚI, CẬP NHẬT, XÓA)
        if (request.getVariants() != null) {
            log.info("Processing {} variants for product: {}", request.getVariants().size(), productId);

            for (ProductVariantUpdateDTO varUpdate : request.getVariants()) {
                // Nếu có flag shouldDelete = true, xóa variant
                if (varUpdate.getShouldDelete() != null && varUpdate.getShouldDelete()) {
                    if (varUpdate.getId() != null) {
                        ProductVariant variant = variantRepository.findById(varUpdate.getId())
                                .orElseThrow(() -> new RuntimeException("Variant not found with id: " + varUpdate.getId()));

                        // Kiểm tra quyền hạn
                        if (!user.getRole().getRoleName().equals(Role.RoleName.ADMIN)) {
                            if (!variant.getProduct().getShop().getSeller().getId().equals(user.getId())) {
                                throw new RuntimeException("You do not have permission to delete this variant");
                            }
                        }

                        // Soft delete
                        variant.setIsActive(false);
                        variantRepository.save(variant);
                        log.info("Variant {} marked as deleted", varUpdate.getId());
                    }
                    continue;
                }

                // Nếu có ID = cập nhật variant đã tồn tại
                if (varUpdate.getId() != null) {
                    ProductVariant variant = variantRepository.findById(varUpdate.getId())
                            .orElseThrow(() -> new RuntimeException("Variant not found with id: " + varUpdate.getId()));

                    // Kiểm tra quyền hạn
                    if (!user.getRole().getRoleName().equals(Role.RoleName.ADMIN)) {
                        if (!variant.getProduct().getShop().getSeller().getId().equals(user.getId())) {
                            throw new RuntimeException("You do not have permission to update this variant");
                        }
                    }

                    // Cập nhật thông tin variant
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
                }
                // Nếu không có ID = thêm mới variant
                else {
                    if (varUpdate.getVariantTypeId() == null || varUpdate.getVariantValue() == null) {
                        throw new RuntimeException("VariantTypeId and VariantValue are required for new variant");
                    }

                    VariantType variantType = variantTypeRepository.findById(varUpdate.getVariantTypeId())
                            .orElseThrow(() -> new RuntimeException("Variant type not found"));

                    ProductVariant newVariant = new ProductVariant();
                    newVariant.setProduct(product);
                    newVariant.setVariantType(variantType);
                    newVariant.setVariantValue(varUpdate.getVariantValue());
                    newVariant.setPriceAdjustment(varUpdate.getPriceAdjustment() != null ?
                            varUpdate.getPriceAdjustment() : java.math.BigDecimal.ZERO);
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
        log.info("Deleting product: {} by user: {}", productId, user.getId());

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (!user.getRole().getRoleName().equals(Role.RoleName.ADMIN)) {
            if (!product.getShop().getSeller().getId().equals(user.getId())) {
                throw new RuntimeException("You do not have permission to delete this product");
            }
        }

        // Hard delete - XÓA THẬT khỏi database
        // Bước 1: Xóa tất cả variants của product
        variantRepository.deleteByProductId(productId);
        log.info("Deleted all variants for product: {}", productId);

        // Bước 2: Xóa product khỏi giỏ hàng của tất cả users
        cartItemRepository.deleteByProductId(productId);
        log.info("Deleted all cart items for product: {}", productId);

        // Bước 3: Xóa product khỏi wishlist
        wishlistRepository.deleteByProductId(productId);
        log.info("Deleted all wishlist items for product: {}", productId);

        // Bước 4: Xóa product bằng ID (không dùng entity để tránh lỗi Hibernate)
        productRepository.deleteById(productId);

        log.info("Product deleted (hard delete) successfully: {}", productId);
    }

    @Override
    public ProductResponseDTO getProductById(Integer productId) {
        log.info("Getting product by id: {}", productId);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        return mapToProductResponseDTO(product);
    }

    @Override
    public List<ProductResponseDTO> getProductsByShop(Shop shop) {
        log.info("Getting products for shop: {}", shop.getId());

        List<Product> products = productRepository.findByShop(shop);
        return products.stream().map(this::mapToProductResponseDTO).collect(Collectors.toList());
    }

    @Override
    public Page<ProductResponseDTO> getActiveProductsByShop(Integer shopId, Pageable pageable) {
        log.info("Getting active products for shop: {} with pagination", shopId);
        Page<Product> products = productRepository.findActiveByShopId(shopId, pageable);
        return products.map(this::mapToProductResponseDTO);
    }

    @Override
    public Page<ProductResponseDTO> getAllActiveProducts(Pageable pageable) {
        log.info("Getting all active products with pagination");
        Page<Product> products = productRepository.findAllActive(pageable);
        return products.map(this::mapToProductResponseDTO);
    }

    @Override
    public Page<ProductResponseDTO> getProductsByCategory(Integer categoryId, Pageable pageable) {
        log.info("Getting products by category: {} with pagination", categoryId);
        Page<Product> products = productRepository.findByCategoryId(categoryId, pageable);
        return products.map(this::mapToProductResponseDTO);
    }

    @Override
    @Transactional
    public VariantResponseDTO addVariant(Integer productId, CreateVariantRequestDTO request, User user) {
        log.info("Adding variant to product: {} by user: {}", productId, user.getId());

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (!user.getRole().getRoleName().equals(Role.RoleName.ADMIN)) {
            if (!product.getShop().getSeller().getId().equals(user.getId())) {
                throw new RuntimeException("You do not have permission to add variant to this product");
            }
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
        log.info("Updating variant: {} by user: {}", variantId, user.getId());

        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new RuntimeException("Variant not found"));

        if (!user.getRole().getRoleName().equals(Role.RoleName.ADMIN)) {
            if (!variant.getProduct().getShop().getSeller().getId().equals(user.getId())) {
                throw new RuntimeException("You do not have permission to update this variant");
            }
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
        log.info("Deleting variant: {} by user: {}", variantId, user.getId());

        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new RuntimeException("Variant not found"));

        if (!user.getRole().getRoleName().equals(Role.RoleName.ADMIN)) {
            if (!variant.getProduct().getShop().getSeller().getId().equals(user.getId())) {
                throw new RuntimeException("You do not have permission to delete this variant");
            }
        }

        variant.setIsActive(false);
        variantRepository.save(variant);

        log.info("Variant deleted (soft) successfully: {}", variantId);
    }

    @Override
    public List<ProductResponseDTO> getAllProducts() {
        log.info("Getting all products (including inactive)");
        List<Product> products = productRepository.findAll();
        return products.stream().map(this::mapToProductResponseDTO).collect(Collectors.toList());
    }

    private ProductResponseDTO mapToProductResponseDTO(Product product) {
        ProductResponseDTO dto = new ProductResponseDTO();
        dto.setId(product.getId());
        dto.setShopId(product.getShop().getId());
        dto.setShopName(product.getShop().getShopName());
        dto.setCategoryId(product.getCategory().getId());
        dto.setCategoryName(product.getCategory().getName());
        dto.setName(product.getName());
        dto.setDescription(product.getDescription());
        dto.setPrice(product.getPrice());
        dto.setDiscountPrice(product.getDiscountPrice());

        // Xử lý imageUrls khi convert về List
        if (product.getImageUrls() != null && !product.getImageUrls().trim().isEmpty()) {
            List<String> imageUrls = Arrays.stream(product.getImageUrls().split(","))
                    .map(String::trim)
                    .filter(url -> !url.isEmpty())
                    .collect(Collectors.toList());
            dto.setImageUrls(imageUrls.isEmpty() ? null : imageUrls);
        } else {
            dto.setImageUrls(null);
        }

        dto.setIngredients(product.getIngredients());
        dto.setNutritionInfo(product.getNutritionInfo());
        dto.setPreparationTime(product.getPreparationTime());
        dto.setIsAvailable(product.getIsAvailable());
        dto.setStockQuantity(product.getStockQuantity());
        dto.setMinOrderQuantity(product.getMinOrderQuantity());
        dto.setMaxOrderQuantity(product.getMaxOrderQuantity());
        dto.setRating(product.getRating());
        dto.setTotalReviews(product.getTotalReviews());

        // Fix: Xử lý tags khi convert về List
        if (product.getTags() != null && !product.getTags().isEmpty()) {
            dto.setTags(List.of(product.getTags().split(",")));
        } else {
            dto.setTags(null);
        }

        dto.setCreatedAt(product.getCreatedAt());
        dto.setUpdatedAt(product.getUpdatedAt());

        List<ProductVariant> variants = variantRepository.findActiveByProduct(product);
        dto.setVariants(variants.stream().map(this::mapToVariantResponseDTO).collect(Collectors.toList()));

        return dto;
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
}
