package com.example.FoodTourApp.service.impl;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;

/**
 * Service để tạo và validate signed URLs cho file nhạy cảm (IdCard, BusinessLicense)
 * 
 * Flow:
 * 1. Khi trả response, generate signed URL kèm JWT token có thời gian hết hạn (15 phút)
 * 2. Frontend dùng URL này để xem file
 * 3. Backend validate token trước khi serve file
 * 
 * Security:
 * - Token chỉ valid trong 15 phút
 * - Token chứa filePath và userId/role để validate ownership
 * - Chặn path traversal attack
 */
@Service
@Slf4j
public class FileAccessTokenService {

    private final FileStorageService fileStorageService;
    private final SecretKey key;
    
    // Token hết hạn sau 2 ngày (đủ thời gian admin xem và xử lý approval)
    private static final long TOKEN_EXPIRATION = 1000L * 60 * 60 * 24 * 2; // 2 days

    public FileAccessTokenService(
            FileStorageService fileStorageService,
            @Value("${JWT_SECRET}") String jwtSecret) {
        this.fileStorageService = fileStorageService;
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }

    /**
     * Tạo signed URL cho file nhạy cảm
     * 
     * @param relativePath đường dẫn tương đối từ STORAGE_BASE (vd: "IdCard/user_3/abc.jpg")
     * @param fileOwnerId ID của user sở hữu file (user có approval/shop)
     * @param requestUserId ID của user đang request xem file
     * @param requestUserRole Role của user đang request (ADMIN/USER/SELLER)
     * @return signed URL dạng: /public/secure-files?token=xxx
     */
    public String generateSignedUrl(String relativePath, Integer fileOwnerId, Integer requestUserId, String requestUserRole) {
        String token = Jwts.builder()
                .subject("FILE_ACCESS")
                .claim("filePath", relativePath)
                .claim("fileOwnerId", fileOwnerId)  // User sở hữu file
                .claim("requestUserId", requestUserId)  // User đang request
                .claim("requestUserRole", requestUserRole)  // Role của người request
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + TOKEN_EXPIRATION))
                .signWith(key)
                .compact();

        return "/api/public/secure-files?token=" + token;
    }

    /**
     * Validate token và lấy thông tin file
     * 
     * @param token JWT token từ query parameter
     * @return FileAccessInfo chứa filePath và metadata
     * @throws SecurityException nếu token không hợp lệ
     */
    public FileAccessInfo validateTokenAndGetFileInfo(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String subject = claims.getSubject();
            if (!"FILE_ACCESS".equals(subject)) {
                throw new SecurityException("Invalid token type");
            }

            String relativePath = claims.get("filePath", String.class);
            Integer fileOwnerId = claims.get("fileOwnerId", Integer.class);
            Integer requestUserId = claims.get("requestUserId", Integer.class);
            String requestUserRole = claims.get("requestUserRole", String.class);
            
            // Validate ownership: Admin xem tất cả, User/Seller chỉ xem file của mình
            boolean isAdmin = "ADMIN".equals(requestUserRole);
            boolean isOwner = requestUserId.equals(fileOwnerId);
            
            if (!isAdmin && !isOwner) {
                log.warn("Unauthorized file access attempt: userId={} tried to access file of userId={}", 
                        requestUserId, fileOwnerId);
                throw new SecurityException("You don't have permission to access this file");
            }

            // Lấy base directory từ FileStorageService
            String STORAGE_BASE = fileStorageService.getBaseDirectory();

            // Validate path để chặn path traversal
            Path fullPath = Paths.get(STORAGE_BASE, relativePath.replace("/", "\\"))
                    .toAbsolutePath().normalize();
            Path base = Paths.get(STORAGE_BASE).toAbsolutePath().normalize();
            
            if (!fullPath.startsWith(base)) {
                log.warn("Path traversal attempt detected: {}", relativePath);
                throw new SecurityException("Invalid file path");
            }

            // Validate category (IdCard hoặc BusinessLicense)
            String category = relativePath.contains("/") 
                    ? relativePath.substring(0, relativePath.indexOf('/'))
                    : relativePath;
            
            if (!"IdCard".equals(category) && !"BusinessLicense".equals(category)) {
                log.warn("Attempt to access invalid category: {}", category);
                throw new SecurityException("Invalid file category");
            }

            return new FileAccessInfo(fullPath, requestUserRole);

        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            log.error("Token expired: {}", e.getMessage());
            throw new SecurityException("Token expired");
        } catch (io.jsonwebtoken.MalformedJwtException e) {
            log.error("Malformed token: {}", e.getMessage());
            throw new SecurityException("Invalid token");
        } catch (io.jsonwebtoken.security.SignatureException e) {
            log.error("Invalid signature: {}", e.getMessage());
            throw new SecurityException("Invalid token signature");
        } catch (Exception e) {
            log.error("Token validation error: {}", e.getMessage());
            throw new SecurityException("Token validation failed");
        }
    }

    /**
     * DTO chứa thông tin file sau khi validate token
     */
    public static class FileAccessInfo {
        private final Path filePath;
        private final String role;

        public FileAccessInfo(Path filePath, String role) {
            this.filePath = filePath;
            this.role = role;
        }

        public Path getFilePath() {
            return filePath;
        }

        public String getRole() {
            return role;
        }
    }
}
