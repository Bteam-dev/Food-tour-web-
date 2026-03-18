package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.DTO.ErrorResponse;
import com.example.FoodTourApp.service.SecureFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * Implementation của SecureFileService
 * Xử lý logic nghiệp vụ cho việc serve file nhạy cảm
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecureFileServiceImpl implements SecureFileService {

    private final FileAccessTokenService fileAccessTokenService;

    @Override
    public ResponseEntity<?> serveSecureFile(String token) {
        try {
            // Validate token và lấy file info
            FileAccessTokenService.FileAccessInfo fileInfo = fileAccessTokenService.validateTokenAndGetFileInfo(token);
            Path filePath = fileInfo.getFilePath();

            // Load file
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                log.warn("File not found or not readable: {}", filePath);
                return ResponseEntity.notFound().build();
            }

            // Xác định content type
            String contentType = determineContentType(filePath.getFileName().toString());

            log.info("Serving secure file: {} for role: {}", 
                    filePath.getFileName(), fileInfo.getRole());

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filePath.getFileName() + "\"")
                    .body(resource);

        } catch (SecurityException e) {
            log.error("Security error: {}", e.getMessage());
            return ResponseEntity.status(403).body(new ErrorResponse(false, e.getMessage()));
        } catch (Exception e) {
            log.error("Error serving secure file: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(new ErrorResponse(false, "Failed to serve file"));
        }
    }

    /**
     * Xác định content type dựa trên extension
     */
    private String determineContentType(String fileName) {
        String lowerCaseFileName = fileName.toLowerCase();
        
        if (lowerCaseFileName.endsWith(".jpg") || lowerCaseFileName.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG_VALUE;
        } else if (lowerCaseFileName.endsWith(".png")) {
            return MediaType.IMAGE_PNG_VALUE;
        } else if (lowerCaseFileName.endsWith(".pdf")) {
            return MediaType.APPLICATION_PDF_VALUE;
        } else if (lowerCaseFileName.endsWith(".gif")) {
            return MediaType.IMAGE_GIF_VALUE;
        } else if (lowerCaseFileName.endsWith(".webp")) {
            return "image/webp";
        }
        
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }
}
