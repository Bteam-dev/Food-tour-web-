package com.example.FoodTourApp.controller.AdminController;

import com.example.FoodTourApp.config.JWTConfig.JwtUtils;
import com.example.FoodTourApp.service.impl.TokenStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin Session Management Controller
 * Cho phép admin xem và revoke sessions của bất kỳ user nào.
 *
 * Khác với UserSessionController (user chỉ thấy session của mình),
 * admin có thể:
 *   - Xem sessions của bất kỳ userId nào
 *   - Revoke token theo tokenId cụ thể (endpoint đã hoàn thiện)
 *   - Revoke tất cả sessions của một user
 *   - Xem thống kê tổng số active users/sessions
 */
@RestController
@RequestMapping("/api/admin/sessions")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSessionController {

    private static final Logger logger = LoggerFactory.getLogger(AdminSessionController.class);

    private final JwtUtils jwtUtils;
    private final TokenStorageService tokenStorageService;

    public AdminSessionController(JwtUtils jwtUtils, TokenStorageService tokenStorageService) {
        this.jwtUtils = jwtUtils;
        this.tokenStorageService = tokenStorageService;
    }

    /**
     * GET /api/admin/sessions/user/{userId}
     * Lấy tất cả active sessions của một user cụ thể.
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserSessions(@PathVariable Integer userId) {
        try {
            List<TokenStorageService.SessionInfo> sessions = jwtUtils.getUserActiveSessions(userId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("userId", userId);
            response.put("sessions", sessions);
            response.put("sessionCount", sessions.size());

            logger.info("✅ Admin retrieved {} sessions for userId: {}", sessions.size(), userId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("❌ Failed to get sessions for userId: {}", userId, e);
            return ResponseEntity.badRequest().body(
                Map.of("success", false, "message", "Failed to get sessions: " + e.getMessage())
            );
        }
    }

    /**
     * DELETE /api/admin/sessions/revoke/{tokenId}
     * Revoke một token cụ thể theo tokenId (không cần JWT string).
     * Admin lấy tokenId từ GET /sessions/user/{userId} rồi revoke.
     *
     * Lưu ý: tokenId là UUID được tạo bởi TokenStorageService khi user login,
     * không phải JWT string. Xem TokenStorageService.storeAccessToken().
     */
    @DeleteMapping("/revoke/{tokenId}")
    public ResponseEntity<?> revokeSpecificSession(@PathVariable String tokenId) {
        try {
            if (tokenId == null || tokenId.isBlank()) {
                return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", "tokenId is required")
                );
            }

            boolean revoked = jwtUtils.revokeTokenById(tokenId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", revoked);
            response.put("tokenId", tokenId);
            response.put("message", revoked
                ? "Session revoked successfully"
                : "Token not found or already expired");

            if (revoked) {
                logger.info("✅ Admin revoked session tokenId: {}", tokenId);
                return ResponseEntity.ok(response);
            } else {
                logger.warn("⚠️ Admin tried to revoke non-existent tokenId: {}", tokenId);
                return ResponseEntity.ok(response); // Vẫn trả 200 - token hết hạn hay đã revoke trước cũng OK
            }

        } catch (Exception e) {
            logger.error("❌ Failed to revoke session tokenId: {}", tokenId, e);
            return ResponseEntity.badRequest().body(
                Map.of("success", false, "message", "Failed to revoke session: " + e.getMessage())
            );
        }
    }

    /**
     * DELETE /api/admin/sessions/user/{userId}/all
     * Revoke tất cả sessions của một user (force logout all devices).
     * Dùng khi ban user hoặc khi tài khoản bị xâm phạm.
     */
    @DeleteMapping("/user/{userId}/all")
    public ResponseEntity<?> revokeAllUserSessions(@PathVariable Integer userId) {
        try {
            boolean revoked = jwtUtils.logoutAllUserTokens(userId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", revoked);
            response.put("userId", userId);
            response.put("message", revoked
                ? "All sessions revoked for userId: " + userId
                : "Failed to revoke sessions for userId: " + userId);

            logger.info("✅ Admin revoked all sessions for userId: {}", userId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("❌ Failed to revoke all sessions for userId: {}", userId, e);
            return ResponseEntity.badRequest().body(
                Map.of("success", false, "message", "Failed to revoke sessions: " + e.getMessage())
            );
        }
    }

    /**
     * GET /api/admin/sessions/stats
     * Thống kê tổng số active users và sessions trên hệ thống.
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getGlobalStats() {
        try {
            long totalActiveUsers = tokenStorageService.getActiveUserCount();

            Map<String, Object> stats = new HashMap<>();
            stats.put("success", true);
            stats.put("totalActiveUsers", totalActiveUsers);

            logger.info("✅ Admin retrieved global session stats: {} active users", totalActiveUsers);
            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            logger.error("❌ Failed to get global session stats", e);
            return ResponseEntity.badRequest().body(
                Map.of("success", false, "message", "Failed to get stats: " + e.getMessage())
            );
        }
    }
}
