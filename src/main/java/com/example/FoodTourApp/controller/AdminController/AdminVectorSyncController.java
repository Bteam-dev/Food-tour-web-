package com.example.FoodTourApp.controller.AdminController;

import com.example.FoodTourApp.service.FoodVectorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Admin controller để quản lý vector index (Elasticsearch)
 * Chỉ ADMIN mới được quyền resync toàn bộ vector index
 */
@RestController
@RequestMapping("/api/admin/vector")
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminVectorSyncController {

    private final FoodVectorService vectorService;

    @Autowired
    public AdminVectorSyncController(FoodVectorService vectorService) {
        this.vectorService = vectorService;
    }

    /**
     * FORCE RESYNC toàn bộ products vào Elasticsearch
     * Dùng khi:
     * - Cần làm mới index sau khi có nhiều thay đổi
     * - Fix lỗi data cũ trong index
     * - Sau khi thay đổi buildProductText() hoặc embedding logic
     * 
     * CÁCH SỬ DỤNG THỰC TẾ PRODUCTION:
     * 1. Gọi endpoint này qua Postman/curl với token ADMIN
     * 2. Đợi sync xong (check logs)
     * 3. Test lại chatbot
     * 
     * QUAN TRỌNG: Chỉ sync products có isAvailable = true
     */
    @PostMapping("/resync")
    public ResponseEntity<Map<String, Object>> forceResyncAllProducts() {
        Map<String, Object> response = new HashMap<>();
        try {
            log.info("Admin triggered full vector index resync");
            vectorService.syncAllProducts();
            response.put("success", true);
            response.put("message", "Started full vector index resync. Check server logs for progress.");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Resync failed: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Resync failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
