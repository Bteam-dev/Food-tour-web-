package com.example.FoodTourApp.controller.UserController;

import com.example.FoodTourApp.DTO.SellerApprovalDTO.SellerApprovalRequest;
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
@RequestMapping("/api/user/seller-approval")
@PreAuthorize("hasAnyRole('USER','SELLER','ADMIN')")
public class UserSellerApprovalController {

    private static final Logger logger = LoggerFactory.getLogger(UserSellerApprovalController.class);
    private final SellerApprovalService sellerApprovalService;

    public UserSellerApprovalController(SellerApprovalService sellerApprovalService) {
        this.sellerApprovalService = sellerApprovalService;
    }

    @PostMapping("/submit")
    public ResponseEntity<?> submitApproval(@Valid @RequestBody SellerApprovalRequest request,
                                            Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        String userEmail = user.getEmail();
        logger.info("User {} is submitting seller approval request, authorities: {}", userEmail, authentication.getAuthorities());

        if (userEmail == null || userEmail.trim().isEmpty()) {
            logger.error("Invalid user email from authentication");
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Invalid user authentication");
            return ResponseEntity.badRequest().body(error);
        }

        try {
            // Kiểm tra nếu user đã là seller rồi
            boolean isSeller = authentication.getAuthorities().stream()
                    .anyMatch(auth -> auth.getAuthority().equals("SELLER"));

            if (isSeller) {
                logger.error("User {} is already a seller", userEmail);
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "User is already a seller");
                return ResponseEntity.badRequest().body(error);
            }

            SellerApprovalResponse response = sellerApprovalService.submitApproval(userEmail, request);
            logger.info("Seller approval request submitted successfully by user {}", userEmail);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Seller approval request submitted successfully");
            result.put("data", response);

            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            logger.error("Failed to submit seller approval for user {}: {}", userEmail, e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (jakarta.persistence.EntityNotFoundException e) {
            logger.error("User not found for email {}: {}", userEmail, e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "User not found");
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            logger.error("Unexpected error during seller approval submission for user {}: {}", userEmail, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "An unexpected error occurred");
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @GetMapping("/my-approvals")
    public ResponseEntity<?> getMyApprovals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "submittedAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir,
            Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        String userEmail = user.getEmail();
        logger.info("User {} is fetching their approval requests (page: {}, size: {})", userEmail, page, size);

        if (userEmail == null || userEmail.trim().isEmpty()) {
            logger.error("Invalid user email from authentication");
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Invalid user authentication");
            return ResponseEntity.badRequest().body(error);
        }

        try {
            Sort sort = sortDir.equalsIgnoreCase("ASC") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
            Pageable pageable = PageRequest.of(page, size, sort);
            Page<SellerApprovalResponse> approvals = sellerApprovalService.getMyApprovals(userEmail, pageable);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", approvals.getContent());
            result.put("currentPage", approvals.getNumber());
            result.put("totalItems", approvals.getTotalElements());
            result.put("totalPages", approvals.getTotalPages());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error fetching approvals for user {}: {}", userEmail, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to fetch approval requests");
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getApprovalById(@PathVariable Integer id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        String userEmail = user.getEmail();
        logger.info("User {} is fetching approval request with id {}", userEmail, id);

        if (userEmail == null || userEmail.trim().isEmpty()) {
            logger.error("Invalid user email from authentication");
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Invalid user authentication");
            return ResponseEntity.badRequest().body(error);
        }

        try {
            SellerApprovalResponse response = sellerApprovalService.getApprovalById(id);

            // Kiểm tra xem đơn có phải của user này không
            if (!response.getUserEmail().equals(userEmail)) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "You don't have permission to view this approval request");
                return ResponseEntity.status(403).body(error);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", response);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error fetching approval {} for user {}: {}", id, userEmail, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}
