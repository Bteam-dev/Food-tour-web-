package com.example.FoodTourApp.service;

import com.example.FoodTourApp.DTO.SellerApprovalDTO.ReviewApprovalRequest;
import com.example.FoodTourApp.DTO.SellerApprovalDTO.SellerApprovalRequest;
import com.example.FoodTourApp.DTO.SellerApprovalDTO.SellerApprovalResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SellerApprovalService {
    SellerApprovalResponse submitApproval(String userEmail, SellerApprovalRequest request);
    SellerApprovalResponse reviewApproval(String adminEmail, ReviewApprovalRequest request);
    Page<SellerApprovalResponse> getAllPendingApprovals(Pageable pageable);
    Page<SellerApprovalResponse> getAllApprovalsByStatus(String status, Pageable pageable);
    Page<SellerApprovalResponse> getMyApprovals(String userEmail, Pageable pageable);
    SellerApprovalResponse getApprovalById(Integer id);
}
