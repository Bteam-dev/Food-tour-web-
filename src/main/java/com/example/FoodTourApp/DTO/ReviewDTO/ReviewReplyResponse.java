package com.example.FoodTourApp.DTO.ReviewDTO;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ReviewReplyResponse {
    private Integer id;
    private Integer reviewId;
    private Integer userId;
    private String userFullName;
    private String userAvatarUrl;
    private String replyText;
    private List<String> images; // Ảnh đính kèm trong reply
    private String replyType; // SHOP_OWNER hoặc USER
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
