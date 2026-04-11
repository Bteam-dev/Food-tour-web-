package com.example.FoodTourApp.controller.PublicController;

import com.example.FoodTourApp.service.RecommendationDataExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Public API để export training data cho Google Colab
 * Vì Colab không kết nối được localhost, cần deploy BE lên server hoặc dùng ngrok
 * 
 * CÁCH SỬ DỤNG:
 * 1. Deploy BE lên server hoặc dùng ngrok: ngrok http 8080
 * 2. Gọi API: GET https://your-ngrok-url/api/public/recommendation/training-data
 * 3. Download JSON và upload lên Colab
 */
@RestController
@RequestMapping("/api/public/recommendation")
@RequiredArgsConstructor
@Slf4j
public class PublicRecommendationDataController {

    private final RecommendationDataExportService exportService;

    /**
     * GET /api/public/recommendation/training-data
     * Export toàn bộ data cần thiết để train model recommendation
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
        log.info("Exporting training data for recommendation model...");
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
     * GET /api/public/recommendation/training-data/download
     * Download training data as JSON file
     */
    @GetMapping("/training-data/download")
    public ResponseEntity<?> downloadTrainingData() {
        log.info("Downloading training data...");
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
