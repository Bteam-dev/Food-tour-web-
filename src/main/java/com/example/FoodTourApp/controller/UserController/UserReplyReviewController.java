package com.example.FoodTourApp.controller.UserController;

import com.example.FoodTourApp.DTO.ReviewDTO.ReplyRequest;
import com.example.FoodTourApp.DTO.ReviewDTO.ReviewResponse;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/reviews")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class UserReplyReviewController {

    private final ReviewService reviewService;

    /**
     * Admin reply bất kỳ review nào
     * POST /api/admin/reviews/{reviewId}/reply
     */
    @PostMapping("/{reviewId}/reply")
    public ResponseEntity<?> replyReview(
            @PathVariable Integer reviewId,
            @Valid @RequestBody ReplyRequest request,
            @AuthenticationPrincipal User admin) {

        log.info("Admin ID {} is replying to review ID {}", admin.getId(), reviewId);

        try {
            ReviewResponse response = reviewService.replyReview(reviewId, request, admin);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Đã trả lời đánh giá");
            result.put("data", response);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error replying to review: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Có lỗi xảy ra khi trả lời đánh giá");
            return ResponseEntity.badRequest().body(error);
        }
    }
}
