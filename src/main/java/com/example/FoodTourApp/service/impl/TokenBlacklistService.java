package com.example.FoodTourApp.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

/**
 * Token Blacklist Service - Redis-based implementation.
 * Stores blacklisted JWT tokens with automatic expiration (no manual cleanup needed).
 * Called on every authenticated request, so performance is critical.
 */
@Service
public class TokenBlacklistService {

    private static final Logger logger = LoggerFactory.getLogger(TokenBlacklistService.class);
    private static final String BLACKLIST_KEY_PREFIX = "token:blacklist:";

    private final RedisTemplate<String, String> stringRedisTemplate;

    public TokenBlacklistService(RedisTemplate<String, String> stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * Thêm token vào blacklist khi user logout.
     * Redis will automatically remove expired tokens (no cleanup job needed).
     * 
     * @param token JWT token to blacklist
     * @param email User email (for logging purposes)
     * @param expiresAt Token expiration time
     */
    public void blacklistToken(String token, String email, LocalDateTime expiresAt) {
        String key = BLACKLIST_KEY_PREFIX + token;
        
        // Calculate TTL from now until token expiration
        Duration ttl = Duration.between(LocalDateTime.now(), expiresAt);
        
        // Only store if token hasn't expired yet
        if (ttl.isNegative() || ttl.isZero()) {
            logger.warn("Attempted to blacklist already-expired token for email: {}", email);
            return;
        }
        
        // Store email as value (for debugging), with TTL matching token expiration
        stringRedisTemplate.opsForValue().set(key, email, ttl.getSeconds(), TimeUnit.SECONDS);
        
        logger.info("Token blacklisted in Redis for email: {} (TTL: {} seconds)", email, ttl.getSeconds());
    }

    /**
     * Kiểm tra xem token có bị blacklist không.
     * Called on EVERY authenticated request - must be fast!
     * 
     * @param token JWT token to check
     * @return true if token is blacklisted, false otherwise
     */
    public boolean isTokenBlacklisted(String token) {
        String key = BLACKLIST_KEY_PREFIX + token;
        Boolean exists = stringRedisTemplate.hasKey(key);
        return exists != null && exists;
    }
    
    // No cleanup job needed! Redis automatically evicts expired keys.
    // Old @Scheduled cleanup method removed - Redis handles TTL automatically.
}

