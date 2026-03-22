package com.example.FoodTourApp.controller.UserController;

import com.example.FoodTourApp.DTO.RecommendDTO.ProductRecommendDTO;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;
    private static final Logger logger = LoggerFactory.getLogger(RecommendationController.class);

    /**
     * GET /api/user/recommendations/for-you?limit=10
     * YouTube-style: 70% precomputed + 30% ES realtime
     * Mỗi 30 phút thứ tự xáo trộn 1 lần
     * Vừa đặt đơn xong gọi lại → thấy món liên quan mới ngay
     */
    @GetMapping("/for-user")
    public ResponseEntity<?> getForYou(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "10") int limit
    ) {
        logger.info("User {} getting recommendations", user.getId());
        try {
            List<ProductRecommendDTO> recs = recommendationService.getForYou(user.getId(), limit);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", recs);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error getting recommendations for user {}: {}", user.getId(), e.getMessage(), e);
            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", e.getMessage())
            );
        }
    }

    /**
     * GET /api/user/recommendations/similar/{productId}?limit=6
     * Gợi ý món tương tự trên trang chi tiết sản phẩm
     */
    @GetMapping("/similar/{productId}")
    public ResponseEntity<?> getSimilar(
            @AuthenticationPrincipal User user,
            @PathVariable Integer productId,
            @RequestParam(defaultValue = "6") int limit
    ) {
        logger.info("User {} getting similar products for product {}", user.getId(), productId);
        try {
            List<ProductRecommendDTO> similar = recommendationService.getSimilar(productId, limit);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", similar);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error getting similar products {}: {}", productId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", e.getMessage())
            );
        }
    }
}
