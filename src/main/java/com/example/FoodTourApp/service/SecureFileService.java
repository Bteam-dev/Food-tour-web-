package com.example.FoodTourApp.service;

import org.springframework.http.ResponseEntity;

/**
 * Service để xử lý việc serve file nhạy cảm với signed URL
 */
public interface SecureFileService {
    
    /**
     * Serve secure file với token validation
     * 
     * @param token JWT token từ query parameter
     * @return ResponseEntity chứa file resource hoặc error response
     */
    ResponseEntity<?> serveSecureFile(String token);
}
