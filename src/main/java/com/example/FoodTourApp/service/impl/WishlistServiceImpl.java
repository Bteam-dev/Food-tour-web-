package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.DTO.PageResponse;
import com.example.FoodTourApp.DTO.WishlistDTO.WishlistItemDTO;
import com.example.FoodTourApp.DTO.WishlistDTO.WishlistResponse;
import com.example.FoodTourApp.entity.Product;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.entity.Wishlist;
import com.example.FoodTourApp.repository.ProductRepository;
import com.example.FoodTourApp.repository.UserRepository;
import com.example.FoodTourApp.repository.WishlistRepository;
import com.example.FoodTourApp.service.WishlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WishlistServiceImpl implements WishlistService {

    private final WishlistRepository wishlistRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public WishlistResponse addToWishlist(Integer userId, Integer productId) {
        // Kiểm tra user tồn tại
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User không tồn tại"));

        // Kiểm tra product tồn tại
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Sản phẩm không tồn tại"));

        // Kiểm tra đã có trong wishlist chưa
        if (wishlistRepository.existsByUserIdAndProductId(userId, productId)) {
            long totalItems = wishlistRepository.countByUserId(userId);
            return new WishlistResponse("Sản phẩm đã có trong danh sách yêu thích", true, null, totalItems);
        }

        // Thêm vào wishlist
        Wishlist wishlist = new Wishlist();
        wishlist.setUser(user);
        wishlist.setProduct(product);
        wishlist = wishlistRepository.save(wishlist);

        long totalItems = wishlistRepository.countByUserId(userId);
        return new WishlistResponse("Đã thêm sản phẩm vào danh sách yêu thích", true, wishlist.getId(), totalItems);
    }

    @Override
    @Transactional
    public WishlistResponse removeFromWishlist(Integer userId, Integer productId) {
        // Kiểm tra wishlist item tồn tại
        Wishlist wishlist = wishlistRepository.findByUserIdAndProductId(userId, productId)
                .orElseThrow(() -> new RuntimeException("Sản phẩm không có trong danh sách yêu thích"));

        wishlistRepository.delete(wishlist);

        long totalItems = wishlistRepository.countByUserId(userId);
        return new WishlistResponse("Đã xóa sản phẩm khỏi danh sách yêu thích", false, null, totalItems);
    }

    @Override
    @Transactional
    public WishlistResponse toggleWishlist(Integer userId, Integer productId) {
        if (wishlistRepository.existsByUserIdAndProductId(userId, productId)) {
            return removeFromWishlist(userId, productId);
        } else {
            return addToWishlist(userId, productId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<WishlistItemDTO> getUserWishlist(Integer userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Wishlist> wishlistPage = wishlistRepository.findByUserIdWithProductAndShop(userId, pageable);

        Page<WishlistItemDTO> dtoPage = wishlistPage.map(this::convertToDTO);
        return PageResponse.of(dtoPage);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isInWishlist(Integer userId, Integer productId) {
        return wishlistRepository.existsByUserIdAndProductId(userId, productId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countWishlistItems(Integer userId) {
        return wishlistRepository.countByUserId(userId);
    }

    @Override
    @Transactional
    public void clearWishlist(Integer userId) {
        Page<Wishlist> wishlists = wishlistRepository.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(0, Integer.MAX_VALUE));
        wishlistRepository.deleteAll(wishlists.getContent());
    }

    private WishlistItemDTO convertToDTO(Wishlist wishlist) {
        Product product = wishlist.getProduct();
        WishlistItemDTO dto = new WishlistItemDTO();

        dto.setWishlistId(wishlist.getId());
        dto.setProductId(product.getId());
        dto.setProductName(product.getName());
        dto.setDescription(product.getDescription());
        dto.setPrice(product.getPrice());
        dto.setDiscountPrice(product.getDiscountPrice());

        // THÔNG TIN GIẢM GIÁ - Sử dụng helper methods từ Product entity
        dto.setHasDiscount(product.hasDiscount());
        dto.setDiscountPercentage(product.getDiscountPercentage());
        dto.setEffectivePrice(product.getEffectivePrice());

        dto.setIsAvailable(product.getIsAvailable());
        dto.setStockQuantity(product.getStockQuantity());
        dto.setAverageRating(product.getRating());
        dto.setTotalReviews(product.getTotalReviews());
        dto.setAddedAt(wishlist.getCreatedAt());

        // Parse image URLs
        if (product.getImageUrls() != null && !product.getImageUrls().trim().isEmpty()) {
            List<String> imageUrls = Arrays.stream(product.getImageUrls().split(","))
                    .map(String::trim)
                    .filter(url -> !url.isEmpty())
                    .collect(Collectors.toList());
            dto.setImageUrls(imageUrls.isEmpty() ? Collections.emptyList() : imageUrls);
        } else {
            dto.setImageUrls(Collections.emptyList());
        }

        // Shop info
        if (product.getShop() != null) {
            dto.setShopId(product.getShop().getId());
            dto.setShopName(product.getShop().getShopName());
            dto.setShopLogoUrl(product.getShop().getLogoUrl());
            dto.setShopIsActive(product.getShop().getIsActive());
        }

        // Category info
        if (product.getCategory() != null) {
            dto.setCategoryId(product.getCategory().getId());
            dto.setCategoryName(product.getCategory().getName());
        }

        return dto;
    }
}
