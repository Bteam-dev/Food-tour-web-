package com.example.FoodTourApp.repository;

import com.example.FoodTourApp.entity.Product;
import com.example.FoodTourApp.entity.Shop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Integer> {

    // Tìm product theo shop
    List<Product> findByShop(Shop shop);

    // Tìm product active theo shop
    @Query("SELECT p FROM Product p WHERE p.shop = :shop AND p.isAvailable = true")
    List<Product> findActiveByShop(@Param("shop") Shop shop);

    // Tìm product theo category
    @Query("SELECT p FROM Product p WHERE p.category.id = :categoryId")
    List<Product> findByCategoryId(@Param("categoryId") Integer categoryId);

    // Tìm product active
    @Query("SELECT p FROM Product p WHERE p.isAvailable = true")
    List<Product> findAllActive();
}
