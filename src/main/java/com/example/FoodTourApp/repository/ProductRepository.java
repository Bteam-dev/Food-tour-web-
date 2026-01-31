package com.example.FoodTourApp.repository;

import com.example.FoodTourApp.entity.Product;
import com.example.FoodTourApp.entity.Shop;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Integer> {

    // Tìm product theo shop (cho seller quản lý)
    List<Product> findByShop(Shop shop);

    // Tìm product active theo shop - WITH PAGINATION
    @Query("SELECT p FROM Product p WHERE p.shop.id = :shopId AND p.isAvailable = true")
    Page<Product> findActiveByShopId(@Param("shopId") Integer shopId, Pageable pageable);

    // Tìm product active - WITH PAGINATION
    @Query("SELECT p FROM Product p WHERE p.isAvailable = true")
    Page<Product> findAllActive(Pageable pageable);

    // Tìm product theo category - WITH PAGINATION
    @Query("SELECT p FROM Product p WHERE p.category.id = :categoryId AND p.isAvailable = true")
    Page<Product> findByCategoryId(@Param("categoryId") Integer categoryId, Pageable pageable);
}
