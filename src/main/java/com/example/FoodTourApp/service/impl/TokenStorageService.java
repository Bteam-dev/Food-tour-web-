package com.example.FoodTourApp.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service để quản lý token storage trong Redis với bảo mật cao
 * 
 * Redis Schema:
 * - user:sessions:{userId} → Set{tokenId1, tokenId2, ...}
 * - token:{tokenId} → Hash{type, userId, roles, expiry, deviceInfo, createdAt}
 * - token:mapping:{jwt_hash} → tokenId
 * - refresh:{tokenId} → Hash{userId, roles, expiry}
 * - active:users → Set{userId1, userId2, ...}
 */
@Service
public class TokenStorageService {

    private static final Logger logger = LoggerFactory.getLogger(TokenStorageService.class);
    
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    // Redis key patterns
    private static final String USER_SESSIONS_KEY = "user:sessions:%d";
    private static final String TOKEN_KEY = "token:%s";
    private static final String TOKEN_MAPPING_KEY = "token:mapping:%s";
    private static final String REFRESH_TOKEN_KEY = "refresh:%s";
    private static final String ACTIVE_USERS_KEY = "active:users";

    public TokenStorageService(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Lưu access token vào Redis
     */
    public String storeAccessToken(Integer userId, String jwtToken, List<String> roles, 
                                 long expiryTimeMs, String deviceInfo) {
        try {
            String tokenId = generateTokenId();
            String tokenHash = hashToken(jwtToken);
            long expirySeconds = (expiryTimeMs - System.currentTimeMillis()) / 1000;
            
            if (expirySeconds <= 0) {
                logger.warn("Token expiry time is in the past, not storing token");
                return null;
            }

            // 1. Lưu token details
            Map<String, String> tokenData = new HashMap<>();
            tokenData.put("type", "access");
            tokenData.put("userId", userId.toString());
            tokenData.put("roles", serializeRoles(roles));
            tokenData.put("expiry", String.valueOf(expiryTimeMs));
            tokenData.put("deviceInfo", deviceInfo != null ? deviceInfo : "unknown");
            tokenData.put("createdAt", String.valueOf(System.currentTimeMillis()));

            String tokenKey = String.format(TOKEN_KEY, tokenId);
            stringRedisTemplate.opsForHash().putAll(tokenKey, tokenData);
            stringRedisTemplate.expire(tokenKey, Duration.ofSeconds(expirySeconds));

            // 2. Lưu mapping từ JWT hash → tokenId
            String mappingKey = String.format(TOKEN_MAPPING_KEY, tokenHash);
            stringRedisTemplate.opsForValue().set(mappingKey, tokenId, Duration.ofSeconds(expirySeconds));

            // 3. Add tokenId vào user sessions
            String userSessionsKey = String.format(USER_SESSIONS_KEY, userId);
            stringRedisTemplate.opsForSet().add(userSessionsKey, tokenId);
            stringRedisTemplate.expire(userSessionsKey, Duration.ofSeconds(expirySeconds + 3600)); // +1h buffer

            // 4. Add user vào active users
            stringRedisTemplate.opsForSet().add(ACTIVE_USERS_KEY, userId.toString());

            logger.info("✅ Stored access token - TokenId: {}, UserId: {}, Expiry: {}s", 
                       tokenId, userId, expirySeconds);
            return tokenId;

        } catch (Exception e) {
            logger.error("❌ Failed to store access token for userId: {}", userId, e);
            return null;
        }
    }

    /**
     * Lưu refresh token vào Redis
     */
    public String storeRefreshToken(Integer userId, String jwtToken, List<String> roles, long expiryTimeMs) {
        try {
            String tokenId = generateTokenId();
            String tokenHash = hashToken(jwtToken);
            long expirySeconds = (expiryTimeMs - System.currentTimeMillis()) / 1000;
            
            if (expirySeconds <= 0) {
                logger.warn("Refresh token expiry time is in the past, not storing token");
                return null;
            }

            // 1. Lưu refresh token details
            Map<String, String> refreshData = new HashMap<>();
            refreshData.put("userId", userId.toString());
            refreshData.put("roles", serializeRoles(roles));
            refreshData.put("expiry", String.valueOf(expiryTimeMs));
            refreshData.put("createdAt", String.valueOf(System.currentTimeMillis()));

            String refreshKey = String.format(REFRESH_TOKEN_KEY, tokenId);
            stringRedisTemplate.opsForHash().putAll(refreshKey, refreshData);
            stringRedisTemplate.expire(refreshKey, Duration.ofSeconds(expirySeconds));

            // 2. Lưu mapping từ JWT hash → tokenId (cho refresh token)
            String mappingKey = String.format(TOKEN_MAPPING_KEY, tokenHash);
            stringRedisTemplate.opsForValue().set(mappingKey, "refresh:" + tokenId, Duration.ofSeconds(expirySeconds));

            logger.info("✅ Stored refresh token - TokenId: {}, UserId: {}, Expiry: {}s", 
                       tokenId, userId, expirySeconds);
            return tokenId;

        } catch (Exception e) {
            logger.error("❌ Failed to store refresh token for userId: {}", userId, e);
            return null;
        }
    }

    /**
     * Validate access token từ JWT
     */
    public TokenValidationResult validateAccessToken(String jwtToken) {
        try {
            String tokenHash = hashToken(jwtToken);
            String mappingKey = String.format(TOKEN_MAPPING_KEY, tokenHash);
            
            String tokenId = stringRedisTemplate.opsForValue().get(mappingKey);
            if (tokenId == null || tokenId.startsWith("refresh:")) {
                logger.warn("❌ Token not found or is refresh token: {}", tokenHash.substring(0, 8));
                return TokenValidationResult.invalid();
            }

            String tokenKey = String.format(TOKEN_KEY, tokenId);
            Map<Object, Object> tokenData = stringRedisTemplate.opsForHash().entries(tokenKey);
            
            if (tokenData.isEmpty()) {
                logger.warn("❌ Token data not found for tokenId: {}", tokenId);
                return TokenValidationResult.invalid();
            }

            // Check expiry
            long expiry = Long.parseLong((String) tokenData.get("expiry"));
            if (System.currentTimeMillis() > expiry) {
                logger.warn("❌ Token expired: {}", tokenId);
                return TokenValidationResult.invalid();
            }

            Integer userId = Integer.parseInt((String) tokenData.get("userId"));
            List<String> roles = deserializeRoles((String) tokenData.get("roles"));
            String deviceInfo = (String) tokenData.get("deviceInfo");

            logger.info("✅ Token validated - TokenId: {}, UserId: {}, Roles: {}", 
                       tokenId, userId, roles);

            return TokenValidationResult.valid(userId, roles, tokenId, deviceInfo);

        } catch (Exception e) {
            logger.error("❌ Failed to validate token", e);
            return TokenValidationResult.invalid();
        }
    }

    /**
     * Validate refresh token từ JWT
     */
    public RefreshTokenValidationResult validateRefreshToken(String jwtToken) {
        try {
            String tokenHash = hashToken(jwtToken);
            String mappingKey = String.format(TOKEN_MAPPING_KEY, tokenHash);
            
            String mappingValue = stringRedisTemplate.opsForValue().get(mappingKey);
            if (mappingValue == null || !mappingValue.startsWith("refresh:")) {
                logger.warn("❌ Refresh token not found: {}", tokenHash.substring(0, 8));
                return RefreshTokenValidationResult.invalid();
            }

            String tokenId = mappingValue.substring(8); // Remove "refresh:" prefix
            String refreshKey = String.format(REFRESH_TOKEN_KEY, tokenId);
            Map<Object, Object> refreshData = stringRedisTemplate.opsForHash().entries(refreshKey);
            
            if (refreshData.isEmpty()) {
                logger.warn("❌ Refresh token data not found for tokenId: {}", tokenId);
                return RefreshTokenValidationResult.invalid();
            }

            // Check expiry
            long expiry = Long.parseLong((String) refreshData.get("expiry"));
            if (System.currentTimeMillis() > expiry) {
                logger.warn("❌ Refresh token expired: {}", tokenId);
                return RefreshTokenValidationResult.invalid();
            }

            Integer userId = Integer.parseInt((String) refreshData.get("userId"));
            List<String> roles = deserializeRoles((String) refreshData.get("roles"));

            logger.info("✅ Refresh token validated - TokenId: {}, UserId: {}", tokenId, userId);
            return RefreshTokenValidationResult.valid(userId, roles, tokenId);

        } catch (Exception e) {
            logger.error("❌ Failed to validate refresh token", e);
            return RefreshTokenValidationResult.invalid();
        }
    }

    /**
     * Revoke một token cụ thể
     */
    public boolean revokeToken(String jwtToken) {
        try {
            String tokenHash = hashToken(jwtToken);
            String mappingKey = String.format(TOKEN_MAPPING_KEY, tokenHash);
            String mappingValue = stringRedisTemplate.opsForValue().get(mappingKey);
            
            if (mappingValue == null) {
                logger.warn("Token not found for revocation: {}", tokenHash.substring(0, 8));
                return false;
            }

            // Xóa mapping
            stringRedisTemplate.delete(mappingKey);

            if (mappingValue.startsWith("refresh:")) {
                // Refresh token
                String tokenId = mappingValue.substring(8);
                String refreshKey = String.format(REFRESH_TOKEN_KEY, tokenId);
                stringRedisTemplate.delete(refreshKey);
                logger.info("✅ Refresh token revoked: {}", tokenId);
            } else {
                // Access token
                String tokenId = mappingValue;
                String tokenKey = String.format(TOKEN_KEY, tokenId);
                
                // Get userId to remove from user sessions
                Map<Object, Object> tokenData = stringRedisTemplate.opsForHash().entries(tokenKey);
                if (!tokenData.isEmpty()) {
                    Integer userId = Integer.parseInt((String) tokenData.get("userId"));
                    String userSessionsKey = String.format(USER_SESSIONS_KEY, userId);
                    stringRedisTemplate.opsForSet().remove(userSessionsKey, tokenId);
                }
                
                stringRedisTemplate.delete(tokenKey);
                logger.info("✅ Access token revoked: {}", tokenId);
            }

            return true;

        } catch (Exception e) {
            logger.error("❌ Failed to revoke token", e);
            return false;
        }
    }

    /**
     * Revoke một token cụ thể theo tokenId (không cần JWT - dùng cho admin)
     * Admin biết tokenId từ getUserActiveSessions() và revoke trực tiếp
     */
    public boolean revokeTokenById(String tokenId) {
        try {
            String tokenKey = String.format(TOKEN_KEY, tokenId);
            Map<Object, Object> tokenData = stringRedisTemplate.opsForHash().entries(tokenKey);

            if (tokenData.isEmpty()) {
                logger.warn("Token not found for tokenId-based revocation: {}", tokenId);
                return false;
            }

            Integer userId = Integer.parseInt((String) tokenData.get("userId"));

            // Xóa khỏi user sessions set
            String userSessionsKey = String.format(USER_SESSIONS_KEY, userId);
            stringRedisTemplate.opsForSet().remove(userSessionsKey, tokenId);

            // Xóa token data
            stringRedisTemplate.delete(tokenKey);

            // Kiểm tra còn session nào không, nếu không thì xóa khỏi active users
            Long remaining = stringRedisTemplate.opsForSet().size(userSessionsKey);
            if (remaining == null || remaining == 0) {
                stringRedisTemplate.opsForSet().remove(ACTIVE_USERS_KEY, userId.toString());
            }

            logger.info("✅ Token revoked by ID: {}, UserId: {}", tokenId, userId);
            return true;

        } catch (Exception e) {
            logger.error("❌ Failed to revoke token by ID: {}", tokenId, e);
            return false;
        }
    }

    /**
     * Revoke tất cả tokens của một user (logout all devices)
     */
    public boolean revokeAllUserTokens(Integer userId) {
        try {
            String userSessionsKey = String.format(USER_SESSIONS_KEY, userId);
            Set<String> tokenIds = stringRedisTemplate.opsForSet().members(userSessionsKey);
            
            if (tokenIds == null || tokenIds.isEmpty()) {
                logger.info("No tokens found for userId: {}", userId);
                return true;
            }

            int revokedCount = 0;
            for (String tokenId : tokenIds) {
                // Delete access token
                String tokenKey = String.format(TOKEN_KEY, tokenId);
                Boolean deleted = stringRedisTemplate.delete(tokenKey);
                if (Boolean.TRUE.equals(deleted)) {
                    revokedCount++;
                }
            }

            // Delete user sessions
            stringRedisTemplate.delete(userSessionsKey);
            
            // Remove from active users if no more sessions
            stringRedisTemplate.opsForSet().remove(ACTIVE_USERS_KEY, userId.toString());

            logger.info("✅ Revoked {} tokens for userId: {}", revokedCount, userId);
            return true;

        } catch (Exception e) {
            logger.error("❌ Failed to revoke all tokens for userId: {}", userId, e);
            return false;
        }
    }

    /**
     * Get active sessions cho một user
     */
    public List<SessionInfo> getUserActiveSessions(Integer userId) {
        try {
            String userSessionsKey = String.format(USER_SESSIONS_KEY, userId);
            Set<String> tokenIds = stringRedisTemplate.opsForSet().members(userSessionsKey);
            
            if (tokenIds == null || tokenIds.isEmpty()) {
                return Collections.emptyList();
            }

            List<SessionInfo> sessions = new ArrayList<>();
            for (String tokenId : tokenIds) {
                String tokenKey = String.format(TOKEN_KEY, tokenId);
                Map<Object, Object> tokenData = stringRedisTemplate.opsForHash().entries(tokenKey);
                
                if (!tokenData.isEmpty()) {
                    long expiry = Long.parseLong((String) tokenData.get("expiry"));
                    long createdAt = Long.parseLong((String) tokenData.get("createdAt"));
                    String deviceInfo = (String) tokenData.get("deviceInfo");
                    
                    sessions.add(new SessionInfo(tokenId, deviceInfo, createdAt, expiry));
                }
            }

            return sessions.stream()
                    .sorted((a, b) -> Long.compare(b.createdAt, a.createdAt)) // Newest first
                    .collect(Collectors.toList());

        } catch (Exception e) {
            logger.error("❌ Failed to get active sessions for userId: {}", userId, e);
            return Collections.emptyList();
        }
    }

    /**
     * Get count of active users
     */
    public long getActiveUserCount() {
        try {
            Long count = stringRedisTemplate.opsForSet().size(ACTIVE_USERS_KEY);
            return count != null ? count : 0;
        } catch (Exception e) {
            logger.error("❌ Failed to get active user count", e);
            return 0;
        }
    }

    // =================== PRIVATE HELPER METHODS ===================

    private String generateTokenId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String hashToken(String token) {
        return Integer.toString(token.hashCode());
    }

    private String serializeRoles(List<String> roles) {
        try {
            return objectMapper.writeValueAsString(roles);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize roles", e);
            return "[]";
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> deserializeRoles(String rolesJson) {
        try {
            return objectMapper.readValue(rolesJson, List.class);
        } catch (JsonProcessingException e) {
            logger.error("Failed to deserialize roles", e);
            return Collections.emptyList();
        }
    }

    // =================== RESULT CLASSES ===================

    public static class TokenValidationResult {
        private final boolean valid;
        private final Integer userId;
        private final List<String> roles;
        private final String tokenId;
        private final String deviceInfo;

        private TokenValidationResult(boolean valid, Integer userId, List<String> roles, String tokenId, String deviceInfo) {
            this.valid = valid;
            this.userId = userId;
            this.roles = roles;
            this.tokenId = tokenId;
            this.deviceInfo = deviceInfo;
        }

        public static TokenValidationResult valid(Integer userId, List<String> roles, String tokenId, String deviceInfo) {
            return new TokenValidationResult(true, userId, roles, tokenId, deviceInfo);
        }

        public static TokenValidationResult invalid() {
            return new TokenValidationResult(false, null, null, null, null);
        }

        // Getters
        public boolean isValid() { return valid; }
        public Integer getUserId() { return userId; }
        public List<String> getRoles() { return roles; }
        public String getTokenId() { return tokenId; }
        public String getDeviceInfo() { return deviceInfo; }
    }

    public static class RefreshTokenValidationResult {
        private final boolean valid;
        private final Integer userId;
        private final List<String> roles;
        private final String tokenId;

        private RefreshTokenValidationResult(boolean valid, Integer userId, List<String> roles, String tokenId) {
            this.valid = valid;
            this.userId = userId;
            this.roles = roles;
            this.tokenId = tokenId;
        }

        public static RefreshTokenValidationResult valid(Integer userId, List<String> roles, String tokenId) {
            return new RefreshTokenValidationResult(true, userId, roles, tokenId);
        }

        public static RefreshTokenValidationResult invalid() {
            return new RefreshTokenValidationResult(false, null, null, null);
        }

        // Getters
        public boolean isValid() { return valid; }
        public Integer getUserId() { return userId; }
        public List<String> getRoles() { return roles; }
        public String getTokenId() { return tokenId; }
    }

    public static class SessionInfo {
        public final String tokenId;
        public final String deviceInfo;
        public final long createdAt;
        public final long expiry;

        public SessionInfo(String tokenId, String deviceInfo, long createdAt, long expiry) {
            this.tokenId = tokenId;
            this.deviceInfo = deviceInfo;
            this.createdAt = createdAt;
            this.expiry = expiry;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiry;
        }

        public String getFormattedCreatedAt() {
            return LocalDateTime.ofEpochSecond(createdAt / 1000, 0, ZoneOffset.UTC).toString();
        }

        public String getFormattedExpiry() {
            return LocalDateTime.ofEpochSecond(expiry / 1000, 0, ZoneOffset.UTC).toString();
        }
    }
}