package com.example.FoodTourApp.DTO.SellerApprovalDTO;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class ReviewApprovalRequest {
    @NotNull(message = "Approval ID is required")
    private Integer approvalId;

    @NotBlank(message = "Status is required (APPROVED or REJECTED)")
    private String status; // APPROVED or REJECTED

    private String reviewNotes;
}

