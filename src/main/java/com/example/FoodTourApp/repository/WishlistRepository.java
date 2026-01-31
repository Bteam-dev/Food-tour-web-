package com.example.FoodTourApp.repository;

import com.example.FoodTourApp.entity.Wishlist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WishlistRepository extends JpaRepository<Wishlist, Integer> {

    // Tìm wishlist theo user và product
    Optional<Wishlist> findByUserIdAndProductId(Integer userId, Integer productId);

    // Kiểm tra sản phẩm đã có trong wishlist của user chưa
    boolean existsByUserIdAndProductId(Integer userId, Integer productId);

    // Lấy danh sách wishlist của user (có phân trang)
    Page<Wishlist> findByUserIdOrderByCreatedAtDesc(Integer userId, Pageable pageable);

    // Đếm số lượng sản phẩm trong wishlist của user
    long countByUserId(Integer userId);

    // Xóa theo user và product
    void deleteByUserIdAndProductId(Integer userId, Integer productId);

    // Xóa tất cả wishlist items của một product
    @Modifying
    @Query("DELETE FROM Wishlist w WHERE w.product.id = :productId")
    void deleteByProductId(@Param("productId") Integer productId);

    // Lấy wishlist với thông tin product và shop
    @Query("SELECT w FROM Wishlist w " +
           "JOIN FETCH w.product p " +
           "JOIN FETCH p.shop s " +
           "WHERE w.user.id = :userId " +
           "ORDER BY w.createdAt DESC")
    Page<Wishlist> findByUserIdWithProductAndShop(@Param("userId") Integer userId, Pageable pageable);
}
