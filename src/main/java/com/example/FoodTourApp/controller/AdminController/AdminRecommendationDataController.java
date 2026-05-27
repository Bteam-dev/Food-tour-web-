package com.example.FoodTourApp.controller.AdminController;

import com.example.FoodTourApp.service.RecommendationDataExportService;
import com.example.FoodTourApp.service.RealTimeRecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin API để export training data cho Google Colab.
 * Chỉ Admin mới có thể truy cập — không expose public.
 *
 * CÁCH SỬ DỤNG:
 * 1. Login admin → lấy JWT token
 * 2. Gọi API với Authorization header: Bearer <token>
 *    GET /api/admin/recommendation/training-data
 * 3. Hoặc download JSON: GET /api/admin/recommendation/training-data/download
 * 4. Upload JSON lên Google Colab để train model
 *
 * Nếu BE chạy local + Colab không kết nối được:
 *   - Deploy BE lên server, hoặc
 *   - Dùng ngrok: ngrok http 8080 → dùng URL ngrok + Bearer token
 */
@RestController
@RequestMapping("/api/admin/recommendation")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminRecommendationDataController {

    private final RecommendationDataExportService exportService;
    private final RealTimeRecommendationService recommendationService;

    /**
     * GET /api/admin/recommendation/training-data
     * Export toàn bộ data cần thiết để train model recommendation.
     *
     * Response format:
     * {
     *   "success": true,
     *   "data": {
     *     "interactions": [...],  // User-Product interactions với weights
     *     "products": [...],      // Product info + features
     *     "users": [...],         // User info
     *     "metadata": {...}       // Stats
     *   }
     * }
     */
    @GetMapping("/training-data")
    public ResponseEntity<?> exportTrainingData() {
        log.info("Admin exporting training data for recommendation model...");
        try {
            Map<String, Object> data = exportService.exportAllTrainingData();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", data
            ));
        } catch (Exception e) {
            log.error("Error exporting training data: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        }
    }

    /**
     * POST /api/admin/recommendation/reload-embeddings
     *
     * Hot-reload product embeddings sau khi retrain Two-Tower model trên Colab.
     * Workflow:
     *   1. Retrain model trên Colab → tải product_embeddings.json mới
     *   2. Copy file vào: src/main/resources/models/FoodRecommendSearchByBehavior/model_output/
     *   3. Gọi API này → app load embedding mới ngay, không cần restart
     *   4. (Optional) Sync ES: gọi /api/admin/recommendation/sync-es để update index
     */
    @PostMapping("/reload-embeddings")
    public ResponseEntity<?> reloadEmbeddings() {
        log.info("Admin triggering hot-reload of product embeddings...");
        try {
            int count = recommendationService.reloadProductEmbeddings();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Product embeddings reloaded successfully",
                "count", count
            ));
        } catch (Exception e) {
            log.error("Error reloading embeddings: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        }
    }

    /**
     * GET /api/admin/recommendation/training-data/download
     * Download training data as JSON file.
     */
    @GetMapping("/training-data/download")
    public ResponseEntity<?> downloadTrainingData() {
        log.info("Admin downloading training data...");
        try {
            Map<String, Object> data = exportService.exportAllTrainingData();
            String json = new com.fasterxml.jackson.databind.ObjectMapper()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(data);

            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=training_data.json")
                    .header("Content-Type", "application/json")
                    .body(json);
        } catch (Exception e) {
            log.error("Error downloading training data: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        }
    }
}
