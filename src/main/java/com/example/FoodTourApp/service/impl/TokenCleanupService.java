package com.example.FoodTourApp.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Service để cleanup expired tokens và maintain Redis health
 * 🔥 NEW: Token Cleanup & Maintenance Service
 */
@Service
public class TokenCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(TokenCleanupService.class);
    
    private final StringRedisTemplate stringRedisTemplate;
    private final TokenStorageService tokenStorageService;

    // Redis key patterns
    private static final String USER_SESSIONS_PATTERN = "user:sessions:*";
    private static final String TOKEN_PATTERN = "token:*";
    private static final String TOKEN_MAPPING_PATTERN = "token:mapping:*";
    private static final String REFRESH_TOKEN_PATTERN = "refresh:*";
    private static final String ACTIVE_USERS_KEY = "active:users";

    public TokenCleanupService(StringRedisTemplate stringRedisTemplate, TokenStorageService tokenStorageService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.tokenStorageService = tokenStorageService;
    }

    /**
     * 🔥 NEW: Cleanup expired tokens mỗi 30 phút
     * Redis TTL sẽ tự động xóa, nhưng cần cleanup orphaned keys
     */
    @Scheduled(fixedRate = 30 * 60 * 1000) // 30 minutes
    public void cleanupExpiredTokens() {
        logger.info("🧹 Starting token cleanup process...");
        
        long startTime = System.currentTimeMillis();
        int cleanedCount = 0;
        
        try {
            // Cleanup expired token mappings
            cleanedCount += cleanupTokenMappings();
            
            // Cleanup empty user sessions
            cleanedCount += cleanupEmptyUserSessions();
            
            // Cleanup orphaned refresh tokens
            cleanedCount += cleanupOrphanedRefreshTokens();
            
            // Update active users list
            updateActiveUsersList();
            
            long duration = System.currentTimeMillis() - startTime;
            logger.info("✅ Token cleanup completed! Cleaned {} keys in {}ms", cleanedCount, duration);
            
        } catch (Exception e) {
            logger.error("❌ Token cleanup failed", e);
        }
    }

    /**
     * 🔥 NEW: Deep cleanup mỗi 24 giờ (comprehensive maintenance)
     */
    @Scheduled(fixedRate = 24 * 60 * 60 * 1000) // 24 hours
    public void deepCleanup() {
        logger.info("🧽 Starting deep cleanup process...");
        
        long startTime = System.currentTimeMillis();
        int totalCleaned = 0;
        
        try {
            // Full scan và cleanup tất cả expired keys
            totalCleaned += fullScanCleanup();
            
            // Rebuild active users list từ đầu
            rebuildActiveUsersList();
            
            // Memory optimization
            optimizeRedisMemory();
            
            long duration = System.currentTimeMillis() - startTime;
            logger.info("✅ Deep cleanup completed! Cleaned {} keys in {}ms", totalCleaned, duration);
            
        } catch (Exception e) {
            logger.error("❌ Deep cleanup failed", e);
        }
    }

    /**
     * Cleanup expired token mappings
     */
    private int cleanupTokenMappings() {
        try {
            Set<String> mappingKeys = stringRedisTemplate.keys(TOKEN_MAPPING_PATTERN);
            if (mappingKeys == null || mappingKeys.isEmpty()) {
                return 0;
            }

            int cleanedCount = 0;
            for (String mappingKey : mappingKeys) {
                // Check if mapping still exists (TTL expired)
                if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(mappingKey))) {
                    continue;
                }
                
                String tokenId = stringRedisTemplate.opsForValue().get(mappingKey);
                if (tokenId == null) {
                    // Orphaned mapping
                    stringRedisTemplate.delete(mappingKey);
                    cleanedCount++;
                    continue;
                }

                // Check if corresponding token exists
                String tokenKey;
                if (tokenId.startsWith("refresh:")) {
                    tokenKey = "refresh:" + tokenId.substring(8);
                } else {
                    tokenKey = "token:" + tokenId;
                }

                if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(tokenKey))) {
                    // Token không tồn tại, xóa mapping
                    stringRedisTemplate.delete(mappingKey);
                    cleanedCount++;
                }
            }

            if (cleanedCount > 0) {
                logger.info("🧹 Cleaned {} orphaned token mappings", cleanedCount);
            }
            return cleanedCount;
            
        } catch (Exception e) {
            logger.error("❌ Failed to cleanup token mappings", e);
            return 0;
        }
    }

    /**
     * Cleanup empty user sessions
     */
    private int cleanupEmptyUserSessions() {
        try {
            Set<String> sessionKeys = stringRedisTemplate.keys(USER_SESSIONS_PATTERN);
            if (sessionKeys == null || sessionKeys.isEmpty()) {
                return 0;
            }

            int cleanedCount = 0;
            for (String sessionKey : sessionKeys) {
                Set<String> members = stringRedisTemplate.opsForSet().members(sessionKey);
                if (members == null || members.isEmpty()) {
                    // Empty session set
                    stringRedisTemplate.delete(sessionKey);
                    cleanedCount++;
                } else {
                    // Check if session tokens still exist
                    int validTokens = 0;
                    for (String tokenId : members) {
                        String tokenKey = "token:" + tokenId;
                        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(tokenKey))) {
                            validTokens++;
                        } else {
                            // Remove invalid token from set
                            stringRedisTemplate.opsForSet().remove(sessionKey, tokenId);
                        }
                    }
                    
                    // If no valid tokens left, delete the session
                    if (validTokens == 0) {
                        stringRedisTemplate.delete(sessionKey);
                        cleanedCount++;
                    }
                }
            }

            if (cleanedCount > 0) {
                logger.info("🧹 Cleaned {} empty user sessions", cleanedCount);
            }
            return cleanedCount;
            
        } catch (Exception e) {
            logger.error("❌ Failed to cleanup user sessions", e);
            return 0;
        }
    }

    /**
     * Cleanup orphaned refresh tokens
     */
    private int cleanupOrphanedRefreshTokens() {
        try {
            Set<String> refreshKeys = stringRedisTemplate.keys(REFRESH_TOKEN_PATTERN);
            if (refreshKeys == null || refreshKeys.isEmpty()) {
                return 0;
            }

            int cleanedCount = 0;
            for (String refreshKey : refreshKeys) {
                // Check if refresh token has expired
                Long ttl = stringRedisTemplate.getExpire(refreshKey, TimeUnit.SECONDS);
                if (ttl != null && ttl <= 0) {
                    // Expired but not cleaned up yet
                    stringRedisTemplate.delete(refreshKey);
                    cleanedCount++;
                }
            }

            if (cleanedCount > 0) {
                logger.info("🧹 Cleaned {} orphaned refresh tokens", cleanedCount);
            }
            return cleanedCount;
            
        } catch (Exception e) {
            logger.error("❌ Failed to cleanup refresh tokens", e);
            return 0;
        }
    }

    /**
     * Update active users list based on existing sessions
     */
    private void updateActiveUsersList() {
        try {
            Set<String> sessionKeys = stringRedisTemplate.keys(USER_SESSIONS_PATTERN);
            if (sessionKeys == null || sessionKeys.isEmpty()) {
                // No sessions, clear active users
                stringRedisTemplate.delete(ACTIVE_USERS_KEY);
                return;
            }

            // Extract userIds from session keys
            for (String sessionKey : sessionKeys) {
                if (sessionKey.startsWith("user:sessions:")) {
                    String userId = sessionKey.substring("user:sessions:".length());
                    
                    // Check if user still has valid sessions
                    Set<String> tokens = stringRedisTemplate.opsForSet().members(sessionKey);
                    if (tokens != null && !tokens.isEmpty()) {
                        stringRedisTemplate.opsForSet().add(ACTIVE_USERS_KEY, userId);
                    } else {
                        stringRedisTemplate.opsForSet().remove(ACTIVE_USERS_KEY, userId);
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("❌ Failed to update active users list", e);
        }
    }

    /**
     * Full scan cleanup for comprehensive maintenance
     */
    private int fullScanCleanup() {
        logger.info("🔍 Performing full scan cleanup...");
        
        int totalCleaned = 0;
        totalCleaned += cleanupTokenMappings();
        totalCleaned += cleanupEmptyUserSessions();
        totalCleaned += cleanupOrphanedRefreshTokens();
        
        // Additional cleanup for any remaining expired keys
        try {
            Set<String> allTokenKeys = stringRedisTemplate.keys(TOKEN_PATTERN);
            if (allTokenKeys != null) {
                for (String tokenKey : allTokenKeys) {
                    Long ttl = stringRedisTemplate.getExpire(tokenKey, TimeUnit.SECONDS);
                    if (ttl != null && ttl <= 0) {
                        stringRedisTemplate.delete(tokenKey);
                        totalCleaned++;
                    }
                }
            }
        } catch (Exception e) {
            logger.error("❌ Failed full scan cleanup", e);
        }
        
        return totalCleaned;
    }

    /**
     * Rebuild active users list from scratch
     */
    private void rebuildActiveUsersList() {
        try {
            logger.info("🔄 Rebuilding active users list...");
            
            // Clear current list
            stringRedisTemplate.delete(ACTIVE_USERS_KEY);
            
            // Scan all user sessions and rebuild
            Set<String> sessionKeys = stringRedisTemplate.keys(USER_SESSIONS_PATTERN);
            if (sessionKeys != null) {
                for (String sessionKey : sessionKeys) {
                    if (sessionKey.startsWith("user:sessions:")) {
                        String userId = sessionKey.substring("user:sessions:".length());
                        
                        Set<String> tokens = stringRedisTemplate.opsForSet().members(sessionKey);
                        if (tokens != null && !tokens.isEmpty()) {
                            stringRedisTemplate.opsForSet().add(ACTIVE_USERS_KEY, userId);
                        }
                    }
                }
            }
            
            Long activeCount = stringRedisTemplate.opsForSet().size(ACTIVE_USERS_KEY);
            logger.info("✅ Rebuilt active users list with {} users", activeCount != null ? activeCount : 0);
            
        } catch (Exception e) {
            logger.error("❌ Failed to rebuild active users list", e);
        }
    }

    /**
     * Optimize Redis memory usage
     */
    private void optimizeRedisMemory() {
        try {
            // Set TTL cho active users list để prevent memory leak
            stringRedisTemplate.expire(ACTIVE_USERS_KEY, 24, TimeUnit.HOURS);
            
            logger.info("🎯 Redis memory optimization completed");
            
        } catch (Exception e) {
            logger.error("❌ Failed to optimize Redis memory", e);
        }
    }

    /**
     * 🔥 NEW: Manual cleanup trigger (for admin use)
     */
    public String triggerManualCleanup() {
        logger.info("🚀 Manual cleanup triggered");
        
        long startTime = System.currentTimeMillis();
        int cleanedCount = fullScanCleanup();
        rebuildActiveUsersList();
        optimizeRedisMemory();
        long duration = System.currentTimeMillis() - startTime;
        
        String result = String.format("Manual cleanup completed! Cleaned %d keys in %dms", cleanedCount, duration);
        logger.info("✅ {}", result);
        return result;
    }

    /**
     * 🔥 NEW: Get cleanup statistics
     */
    public TokenCleanupStats getCleanupStats() {
        try {
            Set<String> tokenMappings = stringRedisTemplate.keys(TOKEN_MAPPING_PATTERN);
            Set<String> userSessions = stringRedisTemplate.keys(USER_SESSIONS_PATTERN);
            Set<String> refreshTokens = stringRedisTemplate.keys(REFRESH_TOKEN_PATTERN);
            Long activeUsers = stringRedisTemplate.opsForSet().size(ACTIVE_USERS_KEY);
            
            return new TokenCleanupStats(
                tokenMappings != null ? tokenMappings.size() : 0,
                userSessions != null ? userSessions.size() : 0,
                refreshTokens != null ? refreshTokens.size() : 0,
                activeUsers != null ? activeUsers : 0
            );
            
        } catch (Exception e) {
            logger.error("❌ Failed to get cleanup stats", e);
            return new TokenCleanupStats(0, 0, 0, 0);
        }
    }

    /**
     * Stats class for cleanup information
     */
    public static class TokenCleanupStats {
        public final int tokenMappings;
        public final int userSessions;
        public final int refreshTokens;
        public final long activeUsers;

        public TokenCleanupStats(int tokenMappings, int userSessions, int refreshTokens, long activeUsers) {
            this.tokenMappings = tokenMappings;
            this.userSessions = userSessions;
            this.refreshTokens = refreshTokens;
            this.activeUsers = activeUsers;
        }
    }
}