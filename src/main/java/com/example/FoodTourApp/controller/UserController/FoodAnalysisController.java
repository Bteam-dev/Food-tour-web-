package com.example.FoodTourApp.controller.UserController;

import com.example.FoodTourApp.DTO.FoodAnalysisDTO.FoodAnalysisMessageItem;
import com.example.FoodTourApp.DTO.FoodAnalysisDTO.FoodAnalysisRequest;
import com.example.FoodTourApp.DTO.FoodAnalysisDTO.FoodAnalysisResponse;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.service.FoodAnalysisService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Chatbot phân tích chuyên sâu từng món ăn — yêu cầu đăng nhập.
 * Hoàn toàn độc lập với ChatbotController (chatbot tìm kiếm chung).
 *
 * POST   /api/user/food-analysis/{productId}/chat     — gửi câu hỏi
 * GET    /api/user/food-analysis/{productId}/history  — load lịch sử
 * DELETE /api/user/food-analysis/{productId}/history  — xóa phiên
 */
@RestController
@RequestMapping("/api/user/food-analysis")
@RequiredArgsConstructor
public class FoodAnalysisController {

    private static final Logger log = LoggerFactory.getLogger(FoodAnalysisController.class);

    private final FoodAnalysisService foodAnalysisService;

    @PostMapping("/{productId}/chat")
    public ResponseEntity<?> chat(
            @AuthenticationPrincipal User user,
            @PathVariable Integer productId,
            @Valid @RequestBody FoodAnalysisRequest request) {

        log.info("[FoodAnalysis] user={} product={} | Q: {}", user.getId(), productId, request.getContent());

        try {
            FoodAnalysisResponse response = foodAnalysisService.chat(user, productId, request);
            return ok(response);
        } catch (Exception e) {
            log.error("[FoodAnalysis] chat error user={} product={}: {}", user.getId(), productId, e.getMessage(), e);
            return error(e.getMessage());
        }
    }

    @GetMapping("/{productId}/history")
    public ResponseEntity<?> getHistory(
            @AuthenticationPrincipal User user,
            @PathVariable Integer productId) {

        log.info("[FoodAnalysis] load history user={} product={}", user.getId(), productId);

        try {
            List<FoodAnalysisMessageItem> history = foodAnalysisService.getHistory(user, productId);
            return ok(history);
        } catch (Exception e) {
            log.error("[FoodAnalysis] history error user={} product={}: {}", user.getId(), productId, e.getMessage(), e);
            return error(e.getMessage());
        }
    }

    @DeleteMapping("/{productId}/history")
    public ResponseEntity<?> deleteSession(
            @AuthenticationPrincipal User user,
            @PathVariable Integer productId) {

        log.info("[FoodAnalysis] delete session user={} product={}", user.getId(), productId);

        try {
            foodAnalysisService.deleteSession(user, productId);
            return ok(null);
        } catch (Exception e) {
            log.error("[FoodAnalysis] delete error user={} product={}: {}", user.getId(), productId, e.getMessage(), e);
            return error(e.getMessage());
        }
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> ok(Object data) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("message", "OK");
        body.put("data", data);
        return ResponseEntity.ok(body);
    }

    private ResponseEntity<Map<String, Object>> error(String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", message);
        return ResponseEntity.badRequest().body(body);
    }
}
