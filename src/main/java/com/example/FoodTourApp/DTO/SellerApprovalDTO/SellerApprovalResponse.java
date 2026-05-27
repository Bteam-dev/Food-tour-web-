package com.example.FoodTourApp.DTO.SellerApprovalDTO;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class SellerApprovalResponse {
    private Integer id;
    private Integer userId;
    private String userFullName;
    private String userEmail;
    private String userAvatarUrl;
    private String userPhone;
    private List<String> idCardImageUrls;
    private String facebookUrl;
    private String zaloUrl;
    private String status;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
    private Integer reviewerId;
    private String reviewerFullName;
    private String reviewerAvatarUrl;
    private String reviewNotes;
}
