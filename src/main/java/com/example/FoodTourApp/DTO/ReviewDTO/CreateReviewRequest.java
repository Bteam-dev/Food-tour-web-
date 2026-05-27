package com.example.FoodTourApp.DTO.ReviewDTO;

import lombok.Data;

import javax.validation.constraints.*;

@Data
public class CreateReviewRequest {

    @NotNull(message = "Review type is required")
    private String reviewableType; // "shop" hoặc "product"

    @NotNull(message = "Reviewable ID is required")
    private Integer reviewableId; // ID của shop hoặc product

    private Integer orderId; // Required nếu review product

    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be between 1 and 5")
    @Max(value = 5, message = "Rating must be between 1 and 5")
    private Integer rating;

    @Size(max = 1000, message = "Comment must not exceed 1000 characters")
    private String comment;

    private Boolean isAnonymous = false;

    // YÊU CẦU HOÀN TIỀN khi review (user có thể tick vào nếu muốn)
    private Boolean hasRefundRequest = false;
}
