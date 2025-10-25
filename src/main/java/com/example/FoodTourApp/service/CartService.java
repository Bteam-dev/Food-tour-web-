package com.example.FoodTourApp.service;

import com.example.FoodTourApp.DTO.CartDTO.CartItemResponseDTO;
import com.example.FoodTourApp.DTO.CartDTO.CreateCartItemRequestDTO;
import com.example.FoodTourApp.DTO.CartDTO.UpdateCartItemRequestDTO;
import com.example.FoodTourApp.entity.User;

import java.util.List;

public interface CartService {

    CartItemResponseDTO addToCart(CreateCartItemRequestDTO request, User user);
    CartItemResponseDTO updateCartItem(Integer cartItemId, UpdateCartItemRequestDTO request, User user);
    void removeFromCart(Integer cartItemId, User user);
    void clearCart(User user);
    List<CartItemResponseDTO> getCartItems(User user);
}
