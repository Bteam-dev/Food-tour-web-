package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.DTO.CartDTO.*;
import com.example.FoodTourApp.DTO.ProductVariantDTO.VariantResponseDTO;
import com.example.FoodTourApp.entity.*;
import com.example.FoodTourApp.repository.*;
import com.example.FoodTourApp.service.CartService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ObjectMapper objectMapper;

    // ─────────────────────────────────────────────
    // Helper: lấy hoặc tạo cart cho user
    // ─────────────────────────────────────────────
    @Override
    @Transactional
    public Cart getOrCreateCart(User user) {
        return cartRepository.findByUser(user).orElseGet(() -> {
            Cart cart = new Cart();
            cart.setUser(user);
            cart.setCreatedAt(LocalDateTime.now());
            cart.setUpdatedAt(LocalDateTime.now());
            return cartRepository.save(cart);
        });
    }

    // ─────────────────────────────────────────────
    // Helper: build SHA-256 hash từ sorted variantIds
    // ─────────────────────────────────────────────
    private String buildVariantHash(List<Integer> variantIds) {
        if (variantIds == null || variantIds.isEmpty()) return "none";
        String joined = variantIds.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(joined.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) sb.append('0');
                sb.append(hex);
            }
            return sb.toString();
        } catch (Exception e) {
            return joined;
        }
    }

    // ─────────────────────────────────────────────
    // GET CART
    // ─────────────────────────────────────────────
    @Override
    @Transactional
    public CartResponseDTO getCart(User user) {
        Cart cart = getOrCreateCart(user);
        return mapToCartResponseDTO(cart);
    }

    // ─────────────────────────────────────────────
    // ADD TO CART
    // ─────────────────────────────────────────────
    @Override
    @Transactional
    public CartItemResponseDTO addToCart(CreateCartItemRequestDTO request, User user) {
        log.info("addToCart: user={}, productId={}", user.getId(), request.getProductId());

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (!product.getIsAvailable())
            throw new RuntimeException("Product is not available");

        if (request.getQuantity() < product.getMinOrderQuantity() || request.getQuantity() > product.getMaxOrderQuantity())
            throw new RuntimeException("Quantity must be between " + product.getMinOrderQuantity() + " and " + product.getMaxOrderQuantity());

        // Validate variants
        List<ProductVariant> selectedVariants = validateVariants(request.getVariantIds(), product);

        Cart cart = getOrCreateCart(user);
        String variantHash = buildVariantHash(request.getVariantIds());

        // Nếu đã có item cùng product + variant combination → cộng thêm SL
        CartItem cartItem = cartItemRepository
                .findByCartAndProductAndVariantHash(cart, product, variantHash)
                .orElse(null);

        if (cartItem == null) {
            cartItem = new CartItem();
            cartItem.setCart(cart);
            cartItem.setProduct(product);
            cartItem.setAddedAt(LocalDateTime.now());
            cartItem.setQuantity(request.getQuantity());
        } else {
            int newQty = cartItem.getQuantity() + request.getQuantity();
            cartItem.setQuantity(Math.min(newQty, product.getMaxOrderQuantity()));
        }

        cartItem.setVariantHash(variantHash);
        cartItem.setSelectedVariants(serializeVariants(request.getVariantIds()));
        cartItem.setSpecialInstructions(request.getSpecialInstructions());
        cartItem.setUpdatedAt(LocalDateTime.now());

        cartItem = cartItemRepository.save(cartItem);

        // Cập nhật updatedAt của cart
        cart.setUpdatedAt(LocalDateTime.now());
        cartRepository.save(cart);

        // Tính tổng giỏ hàng để trả về phản hồi rõ ràng
        List<CartItem> allItems = cartItemRepository.findByCart(cart);
        int totalCartItems = allItems.size();
        int totalCartQuantity = allItems.stream().mapToInt(CartItem::getQuantity).sum();

        log.info("addToCart done: cartItemId={}, totalCartItems={}, totalCartQuantity={}",
                cartItem.getId(), totalCartItems, totalCartQuantity);
        CartItemResponseDTO dto = mapToCartItemResponseDTO(cartItem, selectedVariants);
        dto.setTotalCartItems(totalCartItems);
        dto.setTotalCartQuantity(totalCartQuantity);
        return dto;
    }

    // ─────────────────────────────────────────────
    // UPDATE CART ITEM
    // ─────────────────────────────────────────────
    @Override
    @Transactional
    public CartItemResponseDTO updateCartItem(Integer cartItemId, UpdateCartItemRequestDTO request, User user) {
        log.info("updateCartItem: cartItemId={}, user={}", cartItemId, user.getId());

        CartItem cartItem = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new RuntimeException("Cart item not found"));

        if (!cartItem.getCart().getUser().getId().equals(user.getId()))
            throw new RuntimeException("You do not have permission to update this cart item");

        Product product = cartItem.getProduct();

        // Quantity = 0 → xóa item
        if (request.getQuantity() != null) {
            if (request.getQuantity() == 0) {
                cartItemRepository.delete(cartItem);
                updateCartTimestamp(cartItem.getCart());
                return null;
            }
            if (request.getQuantity() < product.getMinOrderQuantity() || request.getQuantity() > product.getMaxOrderQuantity())
                throw new RuntimeException("Quantity must be between " + product.getMinOrderQuantity() + " and " + product.getMaxOrderQuantity());
            cartItem.setQuantity(request.getQuantity());
        }

        List<ProductVariant> selectedVariants;
        if (request.getVariantIds() != null) {
            selectedVariants = validateVariants(request.getVariantIds(), product);
            String newHash = buildVariantHash(request.getVariantIds());
            cartItem.setVariantHash(newHash);
            cartItem.setSelectedVariants(serializeVariants(request.getVariantIds()));
        } else {
            selectedVariants = deserializeVariants(cartItem.getSelectedVariants(), product);
        }

        if (request.getSpecialInstructions() != null)
            cartItem.setSpecialInstructions(request.getSpecialInstructions());

        cartItem.setUpdatedAt(LocalDateTime.now());
        cartItem = cartItemRepository.save(cartItem);
        updateCartTimestamp(cartItem.getCart());

        return mapToCartItemResponseDTO(cartItem, selectedVariants);
    }

    // ─────────────────────────────────────────────
    // REMOVE CART ITEM
    // ─────────────────────────────────────────────
    @Override
    @Transactional
    public void removeCartItem(Integer cartItemId, User user) {
        CartItem cartItem = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new RuntimeException("Cart item not found"));

        if (!cartItem.getCart().getUser().getId().equals(user.getId()))
            throw new RuntimeException("You do not have permission to remove this cart item");

        Cart cart = cartItem.getCart();
        cartItemRepository.delete(cartItem);
        updateCartTimestamp(cart);
        log.info("removeCartItem: cartItemId={} removed", cartItemId);
    }

    // ─────────────────────────────────────────────
    // CLEAR CART
    // ─────────────────────────────────────────────
    @Override
    @Transactional
    public void clearCart(User user) {
        Cart cart = getOrCreateCart(user);
        cartItemRepository.deleteAll(cartItemRepository.findByCart(cart));
        cart.setUpdatedAt(LocalDateTime.now());
        cartRepository.save(cart);
        log.info("clearCart: cartId={} cleared", cart.getId());
    }

    // ═══════════════════════════════════════════════
    // MAPPING
    // ═══════════════════════════════════════════════

    private CartResponseDTO mapToCartResponseDTO(Cart cart) {
        List<CartItem> items = cartItemRepository.findByCart(cart);

        // Group items theo shop
        Map<Integer, List<CartItem>> byShop = items.stream()
                .collect(Collectors.groupingBy(i -> i.getProduct().getShop().getId()));

        List<CartShopGroupDTO> shopGroups = byShop.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    List<CartItem> shopItems = entry.getValue();
                    Shop shop = shopItems.get(0).getProduct().getShop();

                    List<CartItemResponseDTO> itemDTOs = shopItems.stream()
                            .map(item -> mapToCartItemResponseDTO(item, deserializeVariants(item.getSelectedVariants(), item.getProduct())))
                            .collect(Collectors.toList());

                    BigDecimal shopSubtotal = itemDTOs.stream()
                            .map(CartItemResponseDTO::getTotalPrice)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    int shopTotalQty = shopItems.stream().mapToInt(CartItem::getQuantity).sum();

                    CartShopGroupDTO group = new CartShopGroupDTO();
                    group.setShopId(shop.getId());
                    group.setShopName(shop.getShopName());
                    group.setShopLogoUrl(shop.getLogoUrl());
                    group.setItems(itemDTOs);
                    group.setItemCount(shopItems.size());
                    group.setTotalQuantity(shopTotalQty);
                    group.setShopSubtotal(shopSubtotal);
                    return group;
                })
                .collect(Collectors.toList());

        BigDecimal grandTotal = shopGroups.stream()
                .map(CartShopGroupDTO::getShopSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int totalItems = items.size();
        int totalQuantity = items.stream().mapToInt(CartItem::getQuantity).sum();

        CartResponseDTO dto = new CartResponseDTO();
        dto.setCartId(cart.getId());
        dto.setUserId(cart.getUser().getId());
        dto.setShopGroups(shopGroups);
        dto.setTotalItems(totalItems);
        dto.setTotalQuantity(totalQuantity);
        dto.setGrandTotal(grandTotal);
        dto.setUpdatedAt(cart.getUpdatedAt());
        return dto;
    }

    private CartItemResponseDTO mapToCartItemResponseDTO(CartItem cartItem, List<ProductVariant> selectedVariants) {
        Product product = cartItem.getProduct();

        BigDecimal variantAdjustment = selectedVariants.stream()
                .map(v -> v.getPriceAdjustment() != null ? v.getPriceAdjustment() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal effectivePrice = product.getEffectivePrice();
        BigDecimal unitPrice = effectivePrice.add(variantAdjustment);
        BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(cartItem.getQuantity()));

        List<String> imageUrls = List.of();
        if (product.getImageUrls() != null && !product.getImageUrls().isBlank()) {
            try {
                imageUrls = objectMapper.readValue(product.getImageUrls(), new TypeReference<List<String>>() {});
            } catch (Exception e) {
                log.warn("Failed to parse product imageUrls JSON for product {}: {}", product.getId(), e.getMessage());
            }
        }

        CartItemResponseDTO dto = new CartItemResponseDTO();
        dto.setId(cartItem.getId());
        dto.setProductId(product.getId());
        dto.setProductName(product.getName());
        dto.setImageUrls(imageUrls);
        dto.setOriginalPrice(product.getPrice());
        dto.setUnitPrice(unitPrice);
        dto.setQuantity(cartItem.getQuantity());
        dto.setTotalPrice(totalPrice);
        dto.setSelectedVariants(selectedVariants.stream().map(this::mapToVariantDTO).collect(Collectors.toList()));
        dto.setSpecialInstructions(cartItem.getSpecialInstructions());
        dto.setAddedAt(cartItem.getAddedAt());
        dto.setUpdatedAt(cartItem.getUpdatedAt());
        return dto;
    }

    private VariantResponseDTO mapToVariantDTO(ProductVariant variant) {
        VariantResponseDTO dto = new VariantResponseDTO();
        dto.setId(variant.getId());
        dto.setVariantTypeId(variant.getVariantType().getId());
        dto.setVariantTypeName(variant.getVariantType().getName());
        dto.setVariantValue(variant.getVariantValue());
        dto.setPriceAdjustment(variant.getPriceAdjustment());
        dto.setIsActive(variant.getIsActive());
        return dto;
    }

    // ═══════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════

    private List<ProductVariant> validateVariants(List<Integer> variantIds, Product product) {
        if (variantIds == null || variantIds.isEmpty()) return List.of();
        List<ProductVariant> variants = variantRepository.findAllById(variantIds).stream()
                .filter(v -> v.getProduct().getId().equals(product.getId()) && v.getIsActive())
                .collect(Collectors.toList());
        if (variants.size() != variantIds.size())
            throw new RuntimeException("One or more variants are invalid or not active");

        // Kiểm tra ràng buộc SINGLE: mỗi loại biến thể SINGLE chỉ được chọn đúng 1 variant
        Map<Integer, List<ProductVariant>> byType = variants.stream()
                .collect(Collectors.groupingBy(v -> v.getVariantType().getId()));

        for (Map.Entry<Integer, List<ProductVariant>> entry : byType.entrySet()) {
            com.example.FoodTourApp.entity.VariantType vt = entry.getValue().get(0).getVariantType();
            if (vt.getSelectionType() == com.example.FoodTourApp.entity.VariantType.SelectionType.SINGLE
                    && entry.getValue().size() > 1) {
                throw new RuntimeException(
                        "Loại biến thể '" + vt.getName() + "' chỉ được chọn 1 giá trị (SINGLE)");
            }
        }

        return variants;
    }

    private List<ProductVariant> deserializeVariants(String selectedVariantsJson, Product product) {
        if (selectedVariantsJson == null) return List.of();
        try {
            Map<String, List<Integer>> map = objectMapper.readValue(selectedVariantsJson, Map.class);
            List<Integer> ids = map.getOrDefault("variantIds", List.of());
            if (ids.isEmpty()) return List.of();
            return variantRepository.findAllById(ids).stream()
                    .filter(v -> v.getProduct().getId().equals(product.getId()) && v.getIsActive())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("deserializeVariants error: {}", e.getMessage());
            return List.of();
        }
    }

    private String serializeVariants(List<Integer> variantIds) {
        try {
            Map<String, List<Integer>> map = new HashMap<>();
            map.put("variantIds", variantIds != null ? variantIds : List.of());
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            throw new RuntimeException("Error serializing variants: " + e.getMessage());
        }
    }

    private void updateCartTimestamp(Cart cart) {
        cart.setUpdatedAt(LocalDateTime.now());
        cartRepository.save(cart);
    }
}
