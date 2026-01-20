package com.example.FoodTourApp.service;

import com.example.FoodTourApp.DTO.ReviewDTO.*;
import com.example.FoodTourApp.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface ReviewService {

    // User: Tạo review mới
    ReviewResponse createReview(CreateReviewRequest request, MultipartFile[] images, User user);

    // User: Cập nhật review của mình
    ReviewResponse updateReview(Integer reviewId, UpdateReviewRequest request, MultipartFile[] images, User user);

    // User: Xóa review của mình
    void deleteReview(Integer reviewId, User user);

    // Public: Lấy danh sách review của shop/product
    Page<ReviewResponse> getReviewsByReviewable(String type, Integer id, Pageable pageable);

    // Public: Lấy thống kê review
    ReviewStatistics getReviewStatistics(String type, Integer id);

    // User: Lấy review của user
    Page<ReviewResponse> getMyReviews(User user, Pageable pageable);

    // User: Lấy chi tiết 1 review
    ReviewResponse getReviewById(Integer reviewId);

    // AI CŨNG REPLY ĐƯỢC - Giống Facebook comment
    ReviewResponse replyReview(Integer reviewId, ReplyRequest request, User user);
}
