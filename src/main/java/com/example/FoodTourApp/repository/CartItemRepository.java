package com.example.FoodTourApp.repository;


import com.example.FoodTourApp.entity.CartItem;
import com.example.FoodTourApp.entity.Product;
import com.example.FoodTourApp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Integer> {
    List<CartItem> findByUser(User user);
    Optional<CartItem> findByUserAndProduct(User user, Product product);
}
