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
     * Toggle wishlist: thêm nếu chưa có, xóa nếu đã có.
     * FE dùng khi user bấm icon trái tim trực tiếp từ màn hình sản phẩm.
     */
    WishlistResponse toggleWishlist(Integer userId, Integer productId);

    /**
     * Lấy danh sách wishlist của user (có phân trang)
     */
    PageResponse<WishlistItemDTO> getUserWishlist(Integer userId, int page, int size);

    /**
     * Kiểm tra sản phẩm đã có trong wishlist chưa.
     * FE dùng để tô màu trái tim khi mở trang chi tiết sản phẩm.
     */
    boolean isInWishlist(Integer userId, Integer productId);

    /**
     * Đếm tổng số sản phẩm trong wishlist.
     * FE dùng hiển thị badge số lượng trên icon wishlist.
     */
    long countWishlistItems(Integer userId);

    /**
     * Xóa toàn bộ wishlist của user
     */
    void clearWishlist(Integer userId);
}
