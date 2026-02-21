package com.example.FoodTourApp.service;

import com.example.FoodTourApp.DTO.ReviewDTO.*;
import com.example.FoodTourApp.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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

    // Reply review - CÓ ẢNH (shop owner hoặc user đều reply được)
    ReviewResponse replyReview(Integer reviewId, ReplyRequest request, MultipartFile[] images, User user);

    // Lấy danh sách reply của 1 review
    List<ReviewReplyResponse> getRepliesByReviewId(Integer reviewId);

    // Sửa reply của mình
    ReviewReplyResponse updateReply(Integer replyId, ReplyRequest request, MultipartFile[] images, User user);

    // Xóa reply của mình
    void deleteReply(Integer replyId, User user);
}
