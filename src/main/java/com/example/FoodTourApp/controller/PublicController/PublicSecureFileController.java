package com.example.FoodTourApp.controller.PublicController;

import com.example.FoodTourApp.service.SecureFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public controller để serve file nhạy cảm (IdCard, BusinessLicense) với signed URL
 * 
 * Endpoint này:
 * - KHÔNG yêu cầu authentication (public)
 * - Validate JWT token trong query parameter
 * - Token có thời gian hết hạn (2 ngày)
 * - Chỉ serve file IdCard và BusinessLicense cho ADMIN
 * 
 * URL pattern: GET /api/public/secure-files?token=xxx
 */
@RestController
@RequestMapping("/api/public/secure-files")
@RequiredArgsConstructor
@Slf4j
public class PublicSecureFileController {

    private final SecureFileService secureFileService;

    /**
     * Serve file nhạy cảm với token validation
     * 
     * @param token JWT token chứa file path và role info
     * @return File resource nếu token hợp lệ
     */
    @GetMapping
    public ResponseEntity<?> serveSecureFile(@RequestParam String token) {
        return secureFileService.serveSecureFile(token);
    }
}
