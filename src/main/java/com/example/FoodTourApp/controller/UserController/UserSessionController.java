package com.example.FoodTourApp.controller.UserController;

import com.example.FoodTourApp.config.JWTConfig.JwtUtils;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.service.impl.TokenStorageService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller để quản lý session và token của user
 * 🔥 NEW: Session Management với Redis Token Storage
 */
@RestController
@RequestMapping("/api/user/sessions")
public class UserSessionController {

    private static final Logger logger = LoggerFactory.getLogger(UserSessionController.class);
    
    private final JwtUtils jwtUtils;
    private final TokenStorageService tokenStorageService;

    public UserSessionController(JwtUtils jwtUtils, TokenStorageService tokenStorageService) {
        this.jwtUtils = jwtUtils;
        this.tokenStorageService = tokenStorageService;
    }

    /**
     * 🔥 NEW: Get all active sessions của user hiện tại
     */
    @GetMapping("/active")
    public ResponseEntity<?> getActiveSessions(@AuthenticationPrincipal User user) {
        try {
            Integer userId = user.getId();
            List<TokenStorageService.SessionInfo> sessions = jwtUtils.getUserActiveSessions(userId);
            
            long activeUserCount = tokenStorageService.getActiveUserCount();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("userId", userId);
            response.put("activeSessions", sessions);
            response.put("sessionCount", sessions.size());
            response.put("totalActiveUsers", activeUserCount);
            
            logger.info("✅ Retrieved {} active sessions for userId: {}", sessions.size(), userId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("❌ Failed to get active sessions", e);
            return ResponseEntity.badRequest().body(
                Map.of("success", false, "message", "Failed to get active sessions: " + e.getMessage())
            );
        }
    }

    /**
     * 🔥 NEW: Logout từ device cụ thể (revoke specific token)
     */
    @PostMapping("/logout-device")
    public ResponseEntity<?> logoutFromDevice(@AuthenticationPrincipal User user, 
                                            HttpServletRequest request) {
        try {
            String currentToken = jwtUtils.getJwtFromHeader(request);
            if (currentToken == null) {
                return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", "No token found in request")
                );
            }

            boolean revoked = jwtUtils.logoutToken(currentToken);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", revoked);
            response.put("message", revoked ? "Logged out from this device successfully" : "Failed to logout");
            
            logger.info("✅ User {} logged out from current device: {}", user.getId(), revoked);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("❌ Failed to logout from device", e);
            return ResponseEntity.badRequest().body(
                Map.of("success", false, "message", "Failed to logout: " + e.getMessage())
            );
        }
    }

    /**
     * 🔥 NEW: Logout from all devices (revoke all tokens)
     */
    @PostMapping("/logout-all")
    public ResponseEntity<?> logoutFromAllDevices(@AuthenticationPrincipal User user) {
        try {
            Integer userId = user.getId();
            boolean revoked = jwtUtils.logoutAllUserTokens(userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", revoked);
            response.put("message", revoked ? "Logged out from all devices successfully" : "Failed to logout from all devices");
            response.put("userId", userId);
            
            logger.info("✅ User {} logged out from all devices: {}", userId, revoked);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("❌ Failed to logout from all devices", e);
            return ResponseEntity.badRequest().body(
                Map.of("success", false, "message", "Failed to logout from all devices: " + e.getMessage())
            );
        }
    }

    /**
     * 🔥 NEW: Revoke specific session by tokenId (for admin or advanced users)
     */
    @DeleteMapping("/revoke/{tokenId}")
    public ResponseEntity<?> revokeSpecificSession(@AuthenticationPrincipal User user,
                                                 @PathVariable String tokenId) {
        try {
            // TODO: Implement tokenId-based revocation
            // For now, we'll just return not implemented
            return ResponseEntity.badRequest().body(
                Map.of("success", false, "message", "Token ID based revocation not yet implemented")
            );
            
        } catch (Exception e) {
            logger.error("❌ Failed to revoke specific session", e);
            return ResponseEntity.badRequest().body(
                Map.of("success", false, "message", "Failed to revoke session: " + e.getMessage())
            );
        }
    }

    /**
     * 🔥 NEW: Get session statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getSessionStats(@AuthenticationPrincipal User user) {
        try {
            Integer userId = user.getId();
            List<TokenStorageService.SessionInfo> sessions = jwtUtils.getUserActiveSessions(userId);
            long totalActiveUsers = tokenStorageService.getActiveUserCount();
            
            // Calculate stats
            long activeSessions = sessions.size();
            long expiredSessions = sessions.stream().mapToLong(s -> s.isExpired() ? 1 : 0).sum();
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("success", true);
            stats.put("userId", userId);
            stats.put("activeSessions", activeSessions);
            stats.put("expiredSessions", expiredSessions);
            stats.put("totalActiveUsers", totalActiveUsers);
            
            logger.info("✅ Session stats for userId: {} - Active: {}, Expired: {}", 
                       userId, activeSessions, expiredSessions);
            
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            logger.error("❌ Failed to get session stats", e);
            return ResponseEntity.badRequest().body(
                Map.of("success", false, "message", "Failed to get session stats: " + e.getMessage())
            );
        }
    }
}