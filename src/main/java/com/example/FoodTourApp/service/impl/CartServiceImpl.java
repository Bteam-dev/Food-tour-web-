package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.DTO.CartDTO.CartItemResponseDTO;
import com.example.FoodTourApp.DTO.CartDTO.CreateCartItemRequestDTO;
import com.example.FoodTourApp.DTO.CartDTO.UpdateCartItemRequestDTO;
import com.example.FoodTourApp.DTO.ProductVariantDTO.VariantResponseDTO;
import com.example.FoodTourApp.entity.CartItem;
import com.example.FoodTourApp.entity.Product;
import com.example.FoodTourApp.entity.ProductVariant;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.repository.CartItemRepository;
import com.example.FoodTourApp.repository.ProductRepository;
import com.example.FoodTourApp.repository.ProductVariantRepository;
import com.example.FoodTourApp.service.CartService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartServiceImpl implements CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public CartItemResponseDTO addToCart(CreateCartItemRequestDTO request, User user) {
        log.info("Adding product to cart for user: {}, productId: {}", user.getId(), request.getProductId());

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (!product.getIsAvailable()) {
            throw new RuntimeException("Product is not available");
        }

        if (request.getQuantity() < product.getMinOrderQuantity() || request.getQuantity() > product.getMaxOrderQuantity()) {
            throw new RuntimeException("Quantity must be between " + product.getMinOrderQuantity() + " and " + product.getMaxOrderQuantity());
        }

        List<ProductVariant> selectedVariants = request.getVariantIds() != null ?
                variantRepository.findAllById(request.getVariantIds()).stream()
                        .filter(v -> v.getProduct().getId().equals(product.getId()) && v.getIsActive())
                        .collect(Collectors.toList()) : List.of();

        if (request.getVariantIds() != null && selectedVariants.size() != request.getVariantIds().size()) {
            throw new RuntimeException("One or more variants are invalid or not active");
        }

        CartItem cartItem = cartItemRepository.findByUserAndProduct(user, product)
                .orElse(new CartItem());

        cartItem.setUser(user);
        cartItem.setProduct(product);
        cartItem.setQuantity(request.getQuantity());
        cartItem.setSpecialInstructions(request.getSpecialInstructions());

        try {
            Map<String, List<Integer>> variantMap = new HashMap<>();
            variantMap.put("variantIds", request.getVariantIds() != null ? request.getVariantIds() : List.of());
            cartItem.setSelectedVariants(objectMapper.writeValueAsString(variantMap));
        } catch (Exception e) {
            throw new RuntimeException("Error serializing variants: " + e.getMessage());
        }

        if (cartItem.getId() == null) {
            cartItem.setAddedAt(LocalDateTime.now());
        }

        cartItem = cartItemRepository.save(cartItem);

        log.info("Product added to cart successfully: cartItemId: {}", cartItem.getId());
        return mapToCartItemResponseDTO(cartItem);
    }

    @Override
    @Transactional
    public CartItemResponseDTO updateCartItem(Integer cartItemId, UpdateCartItemRequestDTO request, User user) {
        log.info("Updating cart item: {} for user: {}", cartItemId, user.getId());

        CartItem cartItem = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new RuntimeException("Cart item not found"));

        if (!cartItem.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("You do not have permission to update this cart item");
        }

        Product product = cartItem.getProduct();
        if (request.getQuantity() != null) {
            if (request.getQuantity() == 0) {
                cartItemRepository.delete(cartItem);
                log.info("Cart item deleted due to quantity 0: {}", cartItemId);
                return null;
            }
            if (request.getQuantity() < product.getMinOrderQuantity() || request.getQuantity() > product.getMaxOrderQuantity()) {
                throw new RuntimeException("Quantity must be between " + product.getMinOrderQuantity() + " and " + product.getMaxOrderQuantity());
            }
            cartItem.setQuantity(request.getQuantity());
        }

        if (request.getVariantIds() != null) {
            List<ProductVariant> selectedVariants = variantRepository.findAllById(request.getVariantIds()).stream()
                    .filter(v -> v.getProduct().getId().equals(product.getId()) && v.getIsActive())
                    .collect(Collectors.toList());
            if (selectedVariants.size() != request.getVariantIds().size()) {
                throw new RuntimeException("One or more variants are invalid or not active");
            }
            try {
                Map<String, List<Integer>> variantMap = new HashMap<>();
                variantMap.put("variantIds", request.getVariantIds());
                cartItem.setSelectedVariants(objectMapper.writeValueAsString(variantMap));
            } catch (Exception e) {
                throw new RuntimeException("Error serializing variants: " + e.getMessage());
            }
        }

        if (request.getSpecialInstructions() != null) {
            cartItem.setSpecialInstructions(request.getSpecialInstructions());
        }

        cartItem = cartItemRepository.save(cartItem);

        log.info("Cart item updated successfully: {}", cartItemId);
        return mapToCartItemResponseDTO(cartItem);
    }

    @Override
    @Transactional
    public void removeFromCart(Integer cartItemId, User user) {
        log.info("Removing cart item: {} for user: {}", cartItemId, user.getId());

        CartItem cartItem = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new RuntimeException("Cart item not found"));

        if (!cartItem.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("You do not have permission to remove this cart item");
        }

        cartItemRepository.delete(cartItem);
        log.info("Cart item removed successfully: {}", cartItemId);
    }

    @Override
    @Transactional
    public void clearCart(User user) {
        log.info("Clearing cart for user: {}", user.getId());

        List<CartItem> cartItems = cartItemRepository.findByUser(user);
        cartItemRepository.deleteAll(cartItems);
        log.info("Cart cleared successfully for user: {}", user.getId());
    }

    @Override
    public List<CartItemResponseDTO> getCartItems(User user) {
        log.info("Getting cart items for user: {}", user.getId());

        List<CartItem> cartItems = cartItemRepository.findByUser(user);
        return cartItems.stream().map(this::mapToCartItemResponseDTO).collect(Collectors.toList());
    }

    private CartItemResponseDTO mapToCartItemResponseDTO(CartItem cartItem) {
        CartItemResponseDTO dto = new CartItemResponseDTO();
        dto.setId(cartItem.getId());
        dto.setProductId(cartItem.getProduct().getId());
        dto.setProductName(cartItem.getProduct().getName());

        // Map imageUrls from Product
        if (cartItem.getProduct().getImageUrls() != null && !cartItem.getProduct().getImageUrls().isEmpty()) {
            List<String> imageUrls = Arrays.stream(cartItem.getProduct().getImageUrls().split(","))
                    .map(String::trim)
                    .filter(url -> !url.isEmpty())
                    .collect(Collectors.toList());
            dto.setImageUrls(imageUrls.isEmpty() ? List.of() : imageUrls);
        } else {
            dto.setImageUrls(List.of());
        }

        dto.setUnitPrice(cartItem.getProduct().getPrice());
        dto.setQuantity(cartItem.getQuantity());

        BigDecimal totalPrice = cartItem.getProduct().getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()));
        List<ProductVariant> selectedVariants = List.of();
        try {
            Map<String, List<Integer>> variantMap = objectMapper.readValue(cartItem.getSelectedVariants(), Map.class);
            List<Integer> variantIds = variantMap.getOrDefault("variantIds", List.of());
            selectedVariants = variantRepository.findAllById(variantIds).stream()
                    .filter(v -> v.getProduct().getId().equals(cartItem.getProduct().getId()) && v.getIsActive())
                    .collect(Collectors.toList());
            for (ProductVariant variant : selectedVariants) {
                BigDecimal priceAdjustment = variant.getPriceAdjustment() != null ? variant.getPriceAdjustment() : BigDecimal.ZERO;
                totalPrice = totalPrice.add(priceAdjustment.multiply(BigDecimal.valueOf(cartItem.getQuantity())));
            }
        } catch (Exception e) {
            log.error("Error deserializing variants for cartItem: {}: {}", cartItem.getId(), e.getMessage());
        }
        dto.setTotalPrice(totalPrice);

        dto.setSelectedVariants(selectedVariants.stream().map(this::mapToVariantResponseDTO).collect(Collectors.toList()));
        dto.setSpecialInstructions(cartItem.getSpecialInstructions());
        dto.setAddedAt(cartItem.getAddedAt());

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
