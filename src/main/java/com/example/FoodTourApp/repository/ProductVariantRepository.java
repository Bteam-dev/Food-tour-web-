package com.example.FoodTourApp.repository;

import com.example.FoodTourApp.entity.Product;
import com.example.FoodTourApp.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Integer> {

    // Tìm variants theo product
    List<ProductVariant> findByProduct(Product product);

    // Tìm variants active theo product
    @Query("SELECT v FROM ProductVariant v WHERE v.product = :product AND v.isActive = true")
    List<ProductVariant> findActiveByProduct(@Param("product") Product product);

    // Xóa tất cả variants của một product (for hard delete)
    @Modifying
    @Query("DELETE FROM ProductVariant v WHERE v.product.id = :productId")
    void deleteByProductId(@Param("productId") Integer productId);
}
