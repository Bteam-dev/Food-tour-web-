package com.example.FoodTourApp.service;

import com.example.FoodTourApp.DTO.PageResponse;
import com.example.FoodTourApp.DTO.WishlistDTO.WishlistItemDTO;
import com.example.FoodTourApp.DTO.WishlistDTO.WishlistResponse;

public interface WishlistService {

    /**
     * Thêm sản phẩm vào wishlist
     */
    WishlistResponse addToWishlist(Integer userId, Integer productId);

    /**
     * Xóa sản phẩm khỏi wishlist
     */
    WishlistResponse removeFromWishlist(Integer userId, Integer productId);

    /**
     * Toggle wishlist (thêm nếu chưa có, xóa nếu đã có)
     */
    WishlistResponse toggleWishlist(Integer userId, Integer productId);

    /**
     * Lấy danh sách wishlist của user (có phân trang)
     */
    PageResponse<WishlistItemDTO> getUserWishlist(Integer userId, int page, int size);

    /**
     * Kiểm tra sản phẩm đã có trong wishlist chưa
     */
    boolean isInWishlist(Integer userId, Integer productId);

    /**
     * Đếm số lượng sản phẩm trong wishlist
     */
    long countWishlistItems(Integer userId);

    /**
     * Xóa toàn bộ wishlist của user
     */
    void clearWishlist(Integer userId);
}

