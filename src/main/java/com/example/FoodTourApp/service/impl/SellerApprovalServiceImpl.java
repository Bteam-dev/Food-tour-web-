package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.DTO.SellerApprovalDTO.ReviewApprovalRequest;
import com.example.FoodTourApp.DTO.SellerApprovalDTO.SellerApprovalRequest;
import com.example.FoodTourApp.DTO.SellerApprovalDTO.SellerApprovalResponse;
import com.example.FoodTourApp.entity.Role;
import com.example.FoodTourApp.entity.SellerApproval;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.repository.RoleRepository;
import com.example.FoodTourApp.repository.SellerApprovalRepository;
import com.example.FoodTourApp.repository.UserRepository;
import com.example.FoodTourApp.service.SellerApprovalService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class SellerApprovalServiceImpl implements SellerApprovalService {

    private final SellerApprovalRepository sellerApprovalRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public SellerApprovalServiceImpl(SellerApprovalRepository sellerApprovalRepository,
                                     UserRepository userRepository,
                                     RoleRepository roleRepository) {
        this.sellerApprovalRepository = sellerApprovalRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    @Override
    @Transactional
    public SellerApprovalResponse submitApproval(String userEmail, SellerApprovalRequest request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        // Kiểm tra nếu đã có đơn PENDING
        if (sellerApprovalRepository.existsByUserAndStatus(user, SellerApproval.ApprovalStatus.PENDING)) {
            throw new IllegalArgumentException("You already have a pending approval request");
        }

        SellerApproval approval = new SellerApproval();
        approval.setUser(user);
        approval.setIdCardImageUrl(request.getIdCardImageUrl());
        approval.setFacebookUrl(request.getFacebookUrl());
        approval.setZaloUrl(request.getZaloUrl());
        approval.setStatus(SellerApproval.ApprovalStatus.PENDING);
        approval.setSubmittedAt(LocalDateTime.now());

        SellerApproval savedApproval = sellerApprovalRepository.save(approval);
        return mapToResponse(savedApproval);
    }

    @Override
    @Transactional
    public SellerApprovalResponse reviewApproval(String adminEmail, ReviewApprovalRequest request) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new EntityNotFoundException("Admin not found"));

        SellerApproval approval = sellerApprovalRepository.findById(request.getApprovalId())
                .orElseThrow(() -> new EntityNotFoundException("Approval request not found"));

        // Kiểm tra nếu đơn đã được xử lý rồi
        if (approval.getStatus() != SellerApproval.ApprovalStatus.PENDING) {
            throw new IllegalArgumentException("This approval request has already been reviewed");
        }

        // Validate status
        SellerApproval.ApprovalStatus newStatus;
        try {
            newStatus = SellerApproval.ApprovalStatus.valueOf(request.getStatus().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status. Must be APPROVED or REJECTED");
        }

        if (newStatus == SellerApproval.ApprovalStatus.PENDING) {
            throw new IllegalArgumentException("Cannot set status to PENDING");
        }

        approval.setStatus(newStatus);
        approval.setReviewedAt(LocalDateTime.now());
        approval.setReviewer(admin);
        approval.setReviewNotes(request.getReviewNotes());

        // Nếu APPROVED, cập nhật role của user thành seller
        if (newStatus == SellerApproval.ApprovalStatus.APPROVED) {
            User user = approval.getUser();
            Role sellerRole = roleRepository.findByRoleName(Role.RoleName.SELLER)
                    .orElseThrow(() -> new EntityNotFoundException("Seller role not found"));
            user.setRole(sellerRole);
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
        }

        SellerApproval savedApproval = sellerApprovalRepository.save(approval);
        return mapToResponse(savedApproval);
    }

    @Override
    public Page<SellerApprovalResponse> getAllPendingApprovals(Pageable pageable) {
        Page<SellerApproval> approvals = sellerApprovalRepository.findByStatus(SellerApproval.ApprovalStatus.PENDING, pageable);
        return approvals.map(this::mapToResponse);
    }

    @Override
    public Page<SellerApprovalResponse> getAllApprovalsByStatus(String status, Pageable pageable) {
        Page<SellerApproval> approvals;

        if ("ALL".equalsIgnoreCase(status)) {
            approvals = sellerApprovalRepository.findAll(pageable);
        } else {
            try {
                SellerApproval.ApprovalStatus approvalStatus = SellerApproval.ApprovalStatus.valueOf(status.toUpperCase());
                approvals = sellerApprovalRepository.findByStatus(approvalStatus, pageable);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid status. Must be PENDING, APPROVED, REJECTED, or ALL");
            }
        }

        return approvals.map(this::mapToResponse);
    }

    @Override
    public Page<SellerApprovalResponse> getMyApprovals(String userEmail, Pageable pageable) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        Page<SellerApproval> approvals = sellerApprovalRepository.findByUserId(user.getId(), pageable);
        return approvals.map(this::mapToResponse);
    }

    @Override
    public SellerApprovalResponse getApprovalById(Integer id) {
        SellerApproval approval = sellerApprovalRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Approval request not found"));
        return mapToResponse(approval);
    }

    private SellerApprovalResponse mapToResponse(SellerApproval approval) {
        SellerApprovalResponse response = new SellerApprovalResponse();
        response.setId(approval.getId());
        response.setUserId(approval.getUser().getId());
        response.setUserFullName(approval.getUser().getFullName());
        response.setUserEmail(approval.getUser().getEmail());
        response.setIdCardImageUrl(approval.getIdCardImageUrl());
        response.setFacebookUrl(approval.getFacebookUrl());
        response.setZaloUrl(approval.getZaloUrl());
        response.setStatus(approval.getStatus().toString());
        response.setSubmittedAt(approval.getSubmittedAt());
        response.setReviewedAt(approval.getReviewedAt());

        if (approval.getReviewer() != null) {
            response.setReviewerId(approval.getReviewer().getId());
            response.setReviewerFullName(approval.getReviewer().getFullName());
        }

        response.setReviewNotes(approval.getReviewNotes());
        return response;
    }
}
