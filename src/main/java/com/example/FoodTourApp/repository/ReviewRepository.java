package com.example.FoodTourApp.repository;

import com.example.FoodTourApp.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Integer> {

    // Tìm review theo reviewable type và ID
    Page<Review> findByReviewableTypeAndReviewableIdAndIsApprovedTrue(
            Review.ReviewableType reviewableType,
            Integer reviewableId,
            Pageable pageable
    );

    // Tìm review của user
    Page<Review> findByUserId(Integer userId, Pageable pageable);

    // Kiểm tra user đã review chưa
    boolean existsByUserIdAndReviewableTypeAndReviewableId(
            Integer userId,
            Review.ReviewableType reviewableType,
            Integer reviewableId
    );

    // Kiểm tra user đã review ORDER này với PRODUCT này chưa (cho phép review cùng product từ order khác)
    boolean existsByUserIdAndOrderIdAndReviewableTypeAndReviewableId(
            Integer userId,
            Integer orderId,
            Review.ReviewableType reviewableType,
            Integer reviewableId
    );

    // Tìm review của user cho một reviewable cụ thể
    Optional<Review> findByUserIdAndReviewableTypeAndReviewableId(
            Integer userId,
            Review.ReviewableType reviewableType,
            Integer reviewableId
    );

    // Tính điểm trung bình
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.reviewableType = :type AND r.reviewableId = :id AND r.isApproved = true")
    Double calculateAverageRating(
            @Param("type") Review.ReviewableType type,
            @Param("id") Integer id
    );

    // Đếm số review theo rating
    @Query("SELECT COUNT(r) FROM Review r WHERE r.reviewableType = :type AND r.reviewableId = :id AND r.rating = :rating AND r.isApproved = true")
    Long countByRating(
            @Param("type") Review.ReviewableType type,
            @Param("id") Integer id,
            @Param("rating") Integer rating
    );

    // Tổng số review
    Long countByReviewableTypeAndReviewableIdAndIsApprovedTrue(
            Review.ReviewableType reviewableType,
            Integer reviewableId
    );
}