package com.example.FoodTourApp.service;

import com.example.FoodTourApp.DTO.SellerApprovalDTO.ReviewApprovalRequest;
import com.example.FoodTourApp.DTO.SellerApprovalDTO.SellerApprovalRequest;
import com.example.FoodTourApp.DTO.SellerApprovalDTO.SellerApprovalResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SellerApprovalService {
    /**
     * Nộp đơn xin trở thành seller.
     * Ném IllegalStateException nếu user đã là SELLER rồi.
     */
    SellerApprovalResponse submitApproval(Integer userId, SellerApprovalRequest request);
    SellerApprovalResponse reviewApproval(Integer adminId, ReviewApprovalRequest request);
    Page<SellerApprovalResponse> getAllPendingApprovals(Pageable pageable);
    Page<SellerApprovalResponse> getAllApprovalsByStatus(String status, Pageable pageable);
    Page<SellerApprovalResponse> getMyApprovals(Integer userId, Pageable pageable);
    SellerApprovalResponse getApprovalById(Integer id);
    SellerApprovalResponse getApprovalById(Integer id, Integer userId);
}
