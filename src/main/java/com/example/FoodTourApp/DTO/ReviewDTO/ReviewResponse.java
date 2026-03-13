package com.example.FoodTourApp.DTO.ReviewDTO;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ReviewResponse {
    private Integer id;
    private Integer userId;
    private String userFullName;
    private String userAvatarUrl;
    private String reviewableType; // "shop" hoặc "product"
    private Integer reviewableId;
    private String reviewableName; // Tên shop hoặc product
    private List<String> reviewableImageUrls; // Ảnh của sản phẩm/shop được review
    private Integer orderId;
    private Integer rating;
    private String comment;
    private List<String> images;
    private Boolean isAnonymous;
    private String reply; // Shop owner reply (DEPRECATED - giữ lại để backward compatible)
    private LocalDateTime repliedAt;
    private String repliedByName;
    private String userReply; // User reply lại shop (DEPRECATED - giữ lại để backward compatible)
    private LocalDateTime userRepliedAt;
    private List<ReviewReplyResponse> replies; // NEW: Thread conversation (cãi nhau dài được!)

    // Soft-delete flag – frontend hiển thị "(review đã bị xóa)" khi true
    private Boolean isDeleted;

    // YÊU CẦU HOÀN TIỀN
    private Boolean hasRefundRequest;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
