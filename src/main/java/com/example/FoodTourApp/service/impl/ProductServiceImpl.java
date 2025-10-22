package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.DTO.ProductDTO.CreateProductRequestDTO;
import com.example.FoodTourApp.DTO.ProductDTO.ProductResponseDTO;
import com.example.FoodTourApp.DTO.ProductDTO.UpdateProductRequestDTO;
import com.example.FoodTourApp.DTO.ProductVariantDTO.CreateVariantRequestDTO;
import com.example.FoodTourApp.DTO.ProductVariantDTO.UpdateVariantRequestDTO;
import com.example.FoodTourApp.DTO.ProductVariantDTO.VariantResponseDTO;
import com.example.FoodTourApp.entity.*;
import com.example.FoodTourApp.repository.CategoryRepository;
import com.example.FoodTourApp.repository.ProductRepository;
import com.example.FoodTourApp.repository.ProductVariantRepository;
import com.example.FoodTourApp.repository.ShopRepository;
import com.example.FoodTourApp.service.ProductService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
        product.setImageUrls(request.getImageUrls() != null ? String.join(",", request.getImageUrls()) : null);
        product.setIngredients(request.getIngredients());
        product.setNutritionInfo(request.getNutritionInfo());
        product.setPreparationTime(request.getPreparationTime());
        product.setStockQuantity(request.getStockQuantity());
        product.setMinOrderQuantity(request.getMinOrderQuantity());
        product.setMaxOrderQuantity(request.getMaxOrderQuantity());
        product.setTags(request.getTags() != null ? String.join(",", request.getTags()) : null);
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());

        product = productRepository.save(product);

        if (request.getVariants() != null && !request.getVariants().isEmpty()) {
            for (CreateVariantRequestDTO varReq : request.getVariants()) {
                ProductVariant variant = new ProductVariant();
                variant.setProduct(product);
                variant.setVariantName(varReq.getVariantName());
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
        if (request.getImageUrls() != null) product.setImageUrls(String.join(",", request.getImageUrls()));
        if (request.getIngredients() != null) product.setIngredients(request.getIngredients());
        if (request.getNutritionInfo() != null) product.setNutritionInfo(request.getNutritionInfo());
        if (request.getPreparationTime() != null) product.setPreparationTime(request.getPreparationTime());
        if (request.getIsAvailable() != null) product.setIsAvailable(request.getIsAvailable());
        if (request.getStockQuantity() != null) product.setStockQuantity(request.getStockQuantity());
        if (request.getMinOrderQuantity() != null) product.setMinOrderQuantity(request.getMinOrderQuantity());
        if (request.getMaxOrderQuantity() != null) product.setMaxOrderQuantity(request.getMaxOrderQuantity());
        if (request.getTags() != null) product.setTags(String.join(",", request.getTags()));
        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("Category not found"));
            product.setCategory(category);
        }

        product.setUpdatedAt(LocalDateTime.now());
        product = productRepository.save(product);

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

        product.setIsAvailable(false);
        product.setUpdatedAt(LocalDateTime.now());
        productRepository.save(product);

        log.info("Product deleted (soft) successfully: {}", productId);
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
    public List<ProductResponseDTO> getActiveProductsByShop(Integer shopId) {
        log.info("Getting active products for shop: {}", shopId);

        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new RuntimeException("Shop not found"));

        List<Product> products = productRepository.findActiveByShop(shop);
        return products.stream().map(this::mapToProductResponseDTO).collect(Collectors.toList());
    }

    @Override
    public List<ProductResponseDTO> getAllActiveProducts() {
        log.info("Getting all active products");

        List<Product> products = productRepository.findAllActive();
        return products.stream().map(this::mapToProductResponseDTO).collect(Collectors.toList());
    }

    @Override
    public List<ProductResponseDTO> getProductsByCategory(Integer categoryId) {
        log.info("Getting products by category: {}", categoryId);

        List<Product> products = productRepository.findByCategoryId(categoryId);
        return products.stream().map(this::mapToProductResponseDTO).collect(Collectors.toList());
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

        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setVariantName(request.getVariantName());
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

        if (request.getVariantName() != null) variant.setVariantName(request.getVariantName());
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
        dto.setImageUrls(product.getImageUrls() != null ? List.of(product.getImageUrls().split(",")) : null);
        dto.setIngredients(product.getIngredients());
        dto.setNutritionInfo(product.getNutritionInfo());
        dto.setPreparationTime(product.getPreparationTime());
        dto.setIsAvailable(product.getIsAvailable());
        dto.setStockQuantity(product.getStockQuantity());
        dto.setMinOrderQuantity(product.getMinOrderQuantity());
        dto.setMaxOrderQuantity(product.getMaxOrderQuantity());
        dto.setRating(product.getRating());
        dto.setTotalReviews(product.getTotalReviews());
        dto.setTags(product.getTags() != null ? List.of(product.getTags().split(",")) : null);
        dto.setCreatedAt(product.getCreatedAt());
        dto.setUpdatedAt(product.getUpdatedAt());

        List<ProductVariant> variants = variantRepository.findActiveByProduct(product);
        dto.setVariants(variants.stream().map(this::mapToVariantResponseDTO).collect(Collectors.toList()));

        return dto;
    }

    private VariantResponseDTO mapToVariantResponseDTO(ProductVariant variant) {
        VariantResponseDTO dto = new VariantResponseDTO();
        dto.setId(variant.getId());
        dto.setVariantName(variant.getVariantName());
        dto.setVariantValue(variant.getVariantValue());
        dto.setPriceAdjustment(variant.getPriceAdjustment());
        dto.setIsActive(variant.getIsActive());
        return dto;
    }
}