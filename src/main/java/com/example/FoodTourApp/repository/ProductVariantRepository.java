package com.example.FoodTourApp.repository;

import com.example.FoodTourApp.entity.Product;
import com.example.FoodTourApp.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Integer> {

    // Tìm variants theo product
    List<ProductVariant> findByProduct(Product product);

    // Tìm variants active theo product
    @Query("SELECT v FROM ProductVariant v WHERE v.product = :product AND v.isActive = true")
    List<ProductVariant> findActiveByProduct(@Param("product") Product product);
}
