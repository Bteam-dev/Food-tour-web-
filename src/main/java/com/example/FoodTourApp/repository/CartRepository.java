package com.example.FoodTourApp.repository;

import com.example.FoodTourApp.entity.Cart;
import com.example.FoodTourApp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Integer> {
    Optional<Cart> findByUser(User user);
}

