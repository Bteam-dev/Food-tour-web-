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

    // Tìm product theo tên (search keyword) - WITH PAGINATION
    Page<Product> findByNameContainingIgnoreCase(String keyword, Pageable pageable);

    // ── Lọc nâng cao ──────────────────────────────────────────────────────────

    /**
     * Lọc sản phẩm theo thành phố (qua shop.city), chỉ lấy sản phẩm available.
     * Shop không cần isActive để hiển thị, nhưng isAvailable của sản phẩm phải true.
     */
    @Query("SELECT p FROM Product p WHERE p.isAvailable = true AND LOWER(p.shop.city) = LOWER(:city)")
    Page<Product> findAvailableByCity(@Param("city") String city, Pageable pageable);

    /**
     * Lọc sản phẩm theo thành phố và category.
     */
    @Query("SELECT p FROM Product p WHERE p.isAvailable = true " +
           "AND LOWER(p.shop.city) = LOWER(:city) " +
           "AND p.category.id = :categoryId")
    Page<Product> findAvailableByCityAndCategory(@Param("city") String city,
                                                  @Param("categoryId") Integer categoryId,
                                                  Pageable pageable);

    /**
     * Lấy sản phẩm bán chạy nhất (dựa theo tổng quantity trong order_items đã hoàn thành).
     */
    @Query("SELECT p FROM Product p " +
           "LEFT JOIN OrderItem oi ON oi.product = p " +
           "LEFT JOIN Order o ON oi.order = o " +
           "WHERE p.isAvailable = true " +
           "AND (o IS NULL OR o.orderStatus = com.example.FoodTourApp.entity.Order$OrderStatus.delivered) " +
           "GROUP BY p " +
           "ORDER BY COALESCE(SUM(oi.quantity), 0) DESC")
    Page<Product> findBestSelling(Pageable pageable);

    /**
     * Lấy sản phẩm bán chạy nhất theo thành phố.
     */
    @Query("SELECT p FROM Product p " +
           "LEFT JOIN OrderItem oi ON oi.product = p " +
           "LEFT JOIN Order o ON oi.order = o " +
           "WHERE p.isAvailable = true AND LOWER(p.shop.city) = LOWER(:city) " +
           "AND (o IS NULL OR o.orderStatus = com.example.FoodTourApp.entity.Order$OrderStatus.delivered) " +
           "GROUP BY p " +
           "ORDER BY COALESCE(SUM(oi.quantity), 0) DESC")
    Page<Product> findBestSellingByCity(@Param("city") String city, Pageable pageable);
}
