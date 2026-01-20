package com.example.FoodTourApp.controller.AdminController;

import com.example.FoodTourApp.DTO.SellerApprovalDTO.ReviewApprovalRequest;
import com.example.FoodTourApp.DTO.SellerApprovalDTO.SellerApprovalResponse;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.service.SellerApprovalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/seller-approval")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSellerApprovalController {

    private static final Logger logger = LoggerFactory.getLogger(AdminSellerApprovalController.class);
    private final SellerApprovalService sellerApprovalService;

    public AdminSellerApprovalController(SellerApprovalService sellerApprovalService) {
        this.sellerApprovalService = sellerApprovalService;
    }

    @GetMapping("/pending")
    public ResponseEntity<?> getAllPendingApprovals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "submittedAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir,
            Authentication authentication) {
        User admin = (User) authentication.getPrincipal();
        logger.info("Admin ID {} is fetching all pending approval requests (page: {}, size: {})", admin.getId(), page, size);

        try {
            Sort sort = sortDir.equalsIgnoreCase("ASC") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
            Pageable pageable = PageRequest.of(page, size, sort);
            Page<SellerApprovalResponse> approvals = sellerApprovalService.getAllPendingApprovals(pageable);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", approvals.getContent());
            result.put("currentPage", approvals.getNumber());
            result.put("totalItems", approvals.getTotalElements());
            result.put("totalPages", approvals.getTotalPages());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error fetching pending approvals: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to fetch pending approval requests");
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @PostMapping("/review/{id}")
    public ResponseEntity<?> reviewApproval(@PathVariable Integer id,
                                            @Valid @RequestBody ReviewApprovalRequest request,
                                            Authentication authentication) {
        User admin = (User) authentication.getPrincipal();
        logger.info("Admin ID {} is reviewing approval request {}", admin.getId(), id);

        // Set the approvalId from path variable to ensure consistency
        request.setApprovalId(id);

        try {
            SellerApprovalResponse response = sellerApprovalService.reviewApproval(git addadmin.getId(), request);
            logger.info("Admin ID {} successfully reviewed approval request {} with status {}",
                       admin.getId(), request.getApprovalId(), request.getStatus());

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Approval request reviewed successfully");
            result.put("data", response);

            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            logger.error("Failed to review approval {}: {}", request.getApprovalId(), e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            logger.error("Unexpected error during approval review: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "An unexpected error occurred");
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getApprovalById(@PathVariable Integer id, Authentication authentication) {
        User admin = (User) authentication.getPrincipal();
        logger.info("Admin ID {} is fetching approval request with id {}", admin.getId(), id);

        try {
            SellerApprovalResponse response = sellerApprovalService.getApprovalById(id);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", response);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error fetching approval {}: {}", id, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllApprovals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "submittedAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir,
            Authentication authentication) {
        User admin = (User) authentication.getPrincipal();
        logger.info("Admin ID {} is fetching all approval requests (page: {}, size: {})", admin.getId(), page, size);

        try {
            Sort sort = sortDir.equalsIgnoreCase("ASC") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
            Pageable pageable = PageRequest.of(page, size, sort);
            Page<SellerApprovalResponse> approvals = sellerApprovalService.getAllPendingApprovals(pageable);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", approvals.getContent());
            result.put("currentPage", approvals.getNumber());
            result.put("totalItems", approvals.getTotalElements());
            result.put("totalPages", approvals.getTotalPages());
            result.put("message", "Currently showing pending approvals only. Can be extended to show all.");

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error fetching all approvals: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to fetch approval requests");
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @GetMapping("/filter")
    public ResponseEntity<?> getApprovalsByStatus(
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "submittedAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir,
            Authentication authentication) {
        User admin = (User) authentication.getPrincipal();
        logger.info("Admin ID {} is fetching approval requests with status: {} (page: {}, size: {})", admin.getId(), status, page, size);

        try {
            Sort sort = sortDir.equalsIgnoreCase("ASC") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
            Pageable pageable = PageRequest.of(page, size, sort);
            Page<SellerApprovalResponse> approvals = sellerApprovalService.getAllApprovalsByStatus(status, pageable);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", approvals.getContent());
            result.put("currentPage", approvals.getNumber());
            result.put("totalItems", approvals.getTotalElements());
            result.put("totalPages", approvals.getTotalPages());
            result.put("filter", status);

            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid status filter '{}' requested by admin ID {}: {}", status, admin.getId(), e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            logger.error("Error fetching approvals with status {}: {}", status, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to fetch approval requests");
            return ResponseEntity.internalServerError().body(error);
        }
    }
}
