package com.example.FoodTourApp.service;

import com.example.FoodTourApp.DTO.SellerApprovalDTO.ReviewApprovalRequest;
import com.example.FoodTourApp.DTO.SellerApprovalDTO.SellerApprovalRequest;
import com.example.FoodTourApp.DTO.SellerApprovalDTO.SellerApprovalResponse;

import java.util.List;

public interface SellerApprovalService {
    SellerApprovalResponse submitApproval(String userEmail, SellerApprovalRequest request);
    SellerApprovalResponse reviewApproval(String adminEmail, ReviewApprovalRequest request);
    List<SellerApprovalResponse> getAllPendingApprovals();
    List<SellerApprovalResponse> getAllApprovalsByStatus(String status);
    List<SellerApprovalResponse> getMyApprovals(String userEmail);
    SellerApprovalResponse getApprovalById(Integer id);
}
