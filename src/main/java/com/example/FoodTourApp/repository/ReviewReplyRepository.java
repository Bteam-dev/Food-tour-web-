package com.example.FoodTourApp.repository;

import com.example.FoodTourApp.entity.ReviewReply;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewReplyRepository extends JpaRepository<ReviewReply, Integer> {

    // Lấy tất cả replies của 1 review, sắp xếp theo thời gian
    List<ReviewReply> findByReviewIdOrderByCreatedAtAsc(Integer reviewId);

    // Đếm số reply của 1 review
    Long countByReviewId(Integer reviewId);
}

