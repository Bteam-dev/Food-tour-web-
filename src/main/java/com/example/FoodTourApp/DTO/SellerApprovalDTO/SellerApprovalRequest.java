package com.example.FoodTourApp.DTO.SellerApprovalDTO;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class SellerApprovalRequest {
    @NotBlank(message = "ID card image URL is required")
    private String idCardImageUrl;

    private String facebookUrl;

    private String zaloUrl;
}

