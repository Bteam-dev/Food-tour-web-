package com.example.FoodTourApp.service;

import com.example.FoodTourApp.DTO.CartDTO.CartItemResponseDTO;
import com.example.FoodTourApp.DTO.CartDTO.CartResponseDTO;
import com.example.FoodTourApp.DTO.CartDTO.CreateCartItemRequestDTO;
import com.example.FoodTourApp.DTO.CartDTO.UpdateCartItemRequestDTO;
import com.example.FoodTourApp.entity.Cart;
import com.example.FoodTourApp.entity.User;

public interface CartService {

    /** Lấy hoặc tạo mới cart cho user */
    Cart getOrCreateCart(User user);

    /** Lấy toàn bộ giỏ hàng (đã group theo shop) */
    CartResponseDTO getCart(User user);

    /** Thêm sản phẩm vào giỏ. Nếu đã tồn tại cùng product + variants → cộng thêm SL */
    CartItemResponseDTO addToCart(CreateCartItemRequestDTO request, User user);

    /** Cập nhật số lượng / variants / ghi chú của 1 cart item */
    CartItemResponseDTO updateCartItem(Integer cartItemId, UpdateCartItemRequestDTO request, User user);

    /** Xóa 1 cart item */
    void removeCartItem(Integer cartItemId, User user);

    /** Xóa toàn bộ giỏ hàng */
    void clearCart(User user);
}
