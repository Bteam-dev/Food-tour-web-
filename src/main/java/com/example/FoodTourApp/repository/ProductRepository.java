package com.example.FoodTourApp.repository;

import com.example.FoodTourApp.entity.Product;
import com.example.FoodTourApp.entity.Shop;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
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

    // Tìm product theo tên (search keyword) - WITH PAGINATION - CHỈ LẤY SẢN PHẨM AVAILABLE
    @Query("SELECT p FROM Product p WHERE p.isAvailable = true AND LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Product> findByNameContainingIgnoreCase(@Param("keyword") String keyword, Pageable pageable);

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

    // ── Lọc nâng cao với keyword + price range ─────────────────────────────────

    /**
     * Tìm kiếm full-text theo tên + lọc theo khoảng giá (sử dụng discountPrice nếu có, ngược lại dùng price).
     * Tất cả params đều optional (null = bỏ qua điều kiện đó).
     */
    @Query("SELECT p FROM Product p WHERE p.isAvailable = true " +
           "AND (:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:minPrice IS NULL OR COALESCE(p.discountPrice, p.price) >= :minPrice) " +
           "AND (:maxPrice IS NULL OR COALESCE(p.discountPrice, p.price) <= :maxPrice) " +
           "AND (:categoryId IS NULL OR p.category.id = :categoryId) " +
           "AND (:city IS NULL OR LOWER(p.shop.city) = LOWER(:city))")
    Page<Product> findWithFilters(
            @Param("keyword") String keyword,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("categoryId") Integer categoryId,
            @Param("city") String city,
            Pageable pageable);

    /**
     * Best-selling với keyword + price range + city + category.
     */
    @Query("SELECT p FROM Product p " +
           "LEFT JOIN OrderItem oi ON oi.product = p " +
           "LEFT JOIN Order o ON oi.order = o " +
           "WHERE p.isAvailable = true " +
           "AND (:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:minPrice IS NULL OR COALESCE(p.discountPrice, p.price) >= :minPrice) " +
           "AND (:maxPrice IS NULL OR COALESCE(p.discountPrice, p.price) <= :maxPrice) " +
           "AND (:categoryId IS NULL OR p.category.id = :categoryId) " +
           "AND (:city IS NULL OR LOWER(p.shop.city) = LOWER(:city)) " +
           "AND (o IS NULL OR o.orderStatus = com.example.FoodTourApp.entity.Order$OrderStatus.delivered) " +
           "GROUP BY p " +
           "ORDER BY COALESCE(SUM(oi.quantity), 0) DESC")
    Page<Product> findBestSellingWithFilters(
            @Param("keyword") String keyword,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("categoryId") Integer categoryId,
            @Param("city") String city,
            Pageable pageable);

    // Thêm vào cuối interface ProductRepository

    @Query("""
    SELECT p FROM Product p
    WHERE p.isAvailable = true
    ORDER BY (p.rating * LOG(p.totalReviews + 1)) DESC
    LIMIT :limit
    """)
    List<Product> findTopRatedAvailable(@Param("limit") int limit);

    @Query("""
    SELECT p FROM Product p
    WHERE p.category.id = (
        SELECT p2.category.id FROM Product p2 WHERE p2.id = :productId
    )
    AND p.id != :productId
    AND p.isAvailable = true
    ORDER BY p.rating DESC
    LIMIT :limit
    """)
    List<Product> findSimilarByCategory(
            @Param("productId") Integer productId,
            @Param("limit") int limit
    );

    /**
     * Tìm sản phẩm available theo category ID
     */
    @Query("SELECT p FROM Product p WHERE p.category.id = :categoryId AND p.isAvailable = true ORDER BY p.rating DESC")
    List<Product> findByCategoryIdAndIsAvailableTrue(@Param("categoryId") Integer categoryId, Pageable pageable);

    /**
     * Tìm sản phẩm available theo shop ID
     */
    @Query("SELECT p FROM Product p WHERE p.shop.id = :shopId AND p.isAvailable = true ORDER BY p.rating DESC")
    List<Product> findByShopIdAndIsAvailableTrue(@Param("shopId") Integer shopId, Pageable pageable);
}
