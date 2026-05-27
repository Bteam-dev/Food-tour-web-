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
import com.example.FoodTourApp.service.FCMService;
import com.example.FoodTourApp.service.SellerApprovalService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class SellerApprovalServiceImpl implements SellerApprovalService {

    private final SellerApprovalRepository sellerApprovalRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;
    private final FCMService fcmService;
    private final FileAccessTokenService fileAccessTokenService;

    public SellerApprovalServiceImpl(SellerApprovalRepository sellerApprovalRepository,
                                     UserRepository userRepository,
                                     RoleRepository roleRepository,
                                     FileStorageService fileStorageService,
                                     ObjectMapper objectMapper,
                                     FCMService fcmService,
                                     FileAccessTokenService fileAccessTokenService) {
        this.sellerApprovalRepository = sellerApprovalRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.fileStorageService = fileStorageService;
        this.objectMapper = objectMapper;
        this.fcmService = fcmService;
        this.fileAccessTokenService = fileAccessTokenService;
    }

    @Override
    @Transactional
    public SellerApprovalResponse submitApproval(Integer userId, SellerApprovalRequest request, MultipartFile[] idCardImages) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        // Kiểm tra nếu user đã là SELLER rồi
        if (user.getRole() != null && user.getRole().getRoleName() == Role.RoleName.SELLER) {
            throw new IllegalArgumentException("User is already a seller");
        }

        // Kiểm tra nếu đã có đơn PENDING
        if (sellerApprovalRepository.existsByUserAndStatus(user, SellerApproval.ApprovalStatus.PENDING)) {
            throw new IllegalArgumentException("You already have a pending approval request");
        }

        // Save approval first to get approval ID
        SellerApproval approval = new SellerApproval();
        approval.setUser(user);
        approval.setFacebookUrl(request.getFacebookUrl());
        approval.setZaloUrl(request.getZaloUrl());
        approval.setStatus(SellerApproval.ApprovalStatus.PENDING);
        approval.setSubmittedAt(LocalDateTime.now());
        
        SellerApproval savedApproval = sellerApprovalRepository.save(approval);
        log.info("SellerApproval created with ID {}", savedApproval.getId());

        // Upload ảnh CCCD với hierarchy: user_id/seller_approval_id
        // Lưu dưới dạng relative path để sau này generate signed URL
        List<String> idCardImagePaths;
        try {
            String hierarchyPath = "user_" + userId + "/seller_approval_" + savedApproval.getId();
            idCardImagePaths = fileStorageService.storeSensitiveFilesWithHierarchy(
                    idCardImages,
                    FileStorageService.FileCategory.ID_CARD,
                    hierarchyPath
            );
            log.info("Uploaded {} ID card images with hierarchy: {}", idCardImagePaths.size(), hierarchyPath);
        } catch (IOException e) {
            throw new RuntimeException("Không thể upload ảnh căn cước công dân: " + e.getMessage(), e);
        }

        if (idCardImagePaths.isEmpty()) {
            throw new IllegalArgumentException("Ảnh căn cước công dân là bắt buộc");
        }

        // Lưu danh sách relative paths dưới dạng JSON
        String idCardImageUrlsJson;
        try {
            idCardImageUrlsJson = objectMapper.writeValueAsString(idCardImagePaths);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi xử lý dữ liệu ảnh: " + e.getMessage(), e);
        }

        savedApproval.setIdCardImageUrls(idCardImageUrlsJson);
        savedApproval = sellerApprovalRepository.save(savedApproval);
        return mapToResponse(savedApproval);
    }

    @Override
    @Transactional
    public SellerApprovalResponse reviewApproval(Integer adminId, ReviewApprovalRequest request) {
        User admin = userRepository.findById(adminId)
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

        // Lý do từ chối là bắt buộc khi REJECTED
        if (newStatus == SellerApproval.ApprovalStatus.REJECTED) {
            if (request.getReviewNotes() == null || request.getReviewNotes().isBlank()) {
                throw new IllegalArgumentException("Lý do từ chối là bắt buộc khi từ chối đơn đăng ký");
            }
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

        // Gửi push notification cho user
        User targetUser = approval.getUser();
        try {
            if (newStatus == SellerApproval.ApprovalStatus.APPROVED) {
                fcmService.sendSellerApprovedNotification(targetUser);
            } else if (newStatus == SellerApproval.ApprovalStatus.REJECTED) {
                fcmService.sendSellerRejectedNotification(targetUser, request.getReviewNotes());
            }
        } catch (Exception e) {
            // Không để FCM lỗi ảnh hưởng đến luồng chính
        }

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
    public Page<SellerApprovalResponse> getMyApprovals(Integer userId, Pageable pageable) {
        User user = userRepository.findById(userId)
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

    @Override
    public SellerApprovalResponse getApprovalById(Integer id, Integer userId) {
        SellerApproval approval = sellerApprovalRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Approval request not found"));

        // Kiểm tra quyền sở hữu: chỉ user sở hữu mới được xem
        if (!approval.getUser().getId().equals(userId)) {
            throw new SecurityException("You don't have permission to view this approval request");
        }

        return mapToResponse(approval);
    }

    private SellerApprovalResponse mapToResponse(SellerApproval approval) {
        SellerApprovalResponse response = new SellerApprovalResponse();
        response.setId(approval.getId());

        User user = approval.getUser();
        response.setUserId(user.getId());
        response.setUserFullName(user.getFullName());
        response.setUserEmail(user.getEmail());
        response.setUserAvatarUrl(user.getAvatarUrl());
        response.setUserPhone(user.getPhone());

        // Lấy thông tin user đang request từ Security Context
        Integer requestUserId = null;
        String requestUserRole = "GUEST";
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof User) {
                User requestUser = (User) authentication.getPrincipal();
                requestUserId = requestUser.getId();
                requestUserRole = requestUser.getRole().getRoleName().name();
            }
        } catch (Exception e) {
            // Nếu không lấy được user từ context, có thể là API public
        }

        // Parse JSON array thành List<String> và convert sang signed URLs
        try {
            List<String> relativePaths = objectMapper.readValue(
                    approval.getIdCardImageUrls(),
                    new TypeReference<List<String>>() {}
            );
            
            // Convert relative paths thành signed URLs với ownership validation
            // fileOwnerId = user.getId() (user nộp đơn)
            // requestUserId = người đang xem (từ security context)
            // requestUserRole = role của người xem (ADMIN/USER/SELLER)
            Integer fileOwnerId = user.getId();
            Integer finalRequestUserId = requestUserId != null ? requestUserId : fileOwnerId;
            String finalRequestUserRole = requestUserRole;
            
            List<String> signedUrls = relativePaths.stream()
                    .map(relativePath -> fileAccessTokenService.generateSignedUrl(
                            relativePath, 
                            fileOwnerId, 
                            finalRequestUserId, 
                            finalRequestUserRole
                    ))
                    .toList();
            
            response.setIdCardImageUrls(signedUrls);
        } catch (Exception e) {
            response.setIdCardImageUrls(List.of());
        }

        response.setFacebookUrl(approval.getFacebookUrl());
        response.setZaloUrl(approval.getZaloUrl());
        response.setStatus(approval.getStatus().toString());
        response.setSubmittedAt(approval.getSubmittedAt());
        response.setReviewedAt(approval.getReviewedAt());

        if (approval.getReviewer() != null) {
            User reviewer = approval.getReviewer();
            response.setReviewerId(reviewer.getId());
            response.setReviewerFullName(reviewer.getFullName());
            response.setReviewerAvatarUrl(reviewer.getAvatarUrl());
        }

        response.setReviewNotes(approval.getReviewNotes());
        return response;
    }
}
