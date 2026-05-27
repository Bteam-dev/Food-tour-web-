package com.example.FoodTourApp.repository;


import com.example.FoodTourApp.entity.Cart;
import com.example.FoodTourApp.entity.CartItem;
import com.example.FoodTourApp.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Integer> {

    List<CartItem> findByCart(Cart cart);

    Optional<CartItem> findByCartAndProductAndVariantHash(Cart cart, Product product, String variantHash);

    // Xóa tất cả cart items của một product
    @Modifying
    @Query("DELETE FROM CartItem c WHERE c.product.id = :productId")
    void deleteByProductId(@Param("productId") Integer productId);
}
