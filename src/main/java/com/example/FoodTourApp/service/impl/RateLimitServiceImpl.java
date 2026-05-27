package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.service.RateLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Redis-based rate limiting implementation
 * 
 * Uses Redis INCR with TTL for simple sliding window rate limiting
 * Key pattern: rate:limit:{identifier}:{endpoint}
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitServiceImpl implements RateLimitService {
    
    private final StringRedisTemplate stringRedisTemplate;
    
    private static final String KEY_PREFIX = "rate:limit:";
    
    @Override
    public boolean isAllowed(String identifier, String endpoint, int maxRequests, int windowSeconds) {
        String key = buildKey(identifier, endpoint);
        
        try {
            // Increment counter
            Long currentCount = stringRedisTemplate.opsForValue().increment(key);
            
            if (currentCount == null) {
                log.error("Redis increment returned null for key: {}", key);
                return true; // Fail open - allow request if Redis fails
            }
            
            // Set TTL on first request
            if (currentCount == 1) {
                stringRedisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
            }
            
            boolean allowed = currentCount <= maxRequests;
            
            if (!allowed) {
                log.warn("Rate limit exceeded for {} on endpoint {}: {}/{} requests", 
                    identifier, endpoint, currentCount, maxRequests);
            }
            
            return allowed;
            
        } catch (Exception e) {
            log.error("Error checking rate limit for key {}: {}", key, e.getMessage());
            return true; // Fail open - allow request if Redis fails
        }
    }
    
    @Override
    public int getRemainingRequests(String identifier, String endpoint, int maxRequests) {
        String key = buildKey(identifier, endpoint);
        
        try {
            String countStr = stringRedisTemplate.opsForValue().get(key);
            if (countStr == null) {
                return maxRequests; // No requests yet
            }
            
            int currentCount = Integer.parseInt(countStr);
            return Math.max(0, maxRequests - currentCount);
            
        } catch (Exception e) {
            log.error("Error getting remaining requests for key {}: {}", key, e.getMessage());
            return -1; // Error indicator
        }
    }
    
    @Override
    public void reset(String identifier, String endpoint) {
        String key = buildKey(identifier, endpoint);
        
        try {
            stringRedisTemplate.delete(key);
            log.info("Rate limit reset for {}", key);
        } catch (Exception e) {
            log.error("Error resetting rate limit for key {}: {}", key, e.getMessage());
        }
    }
    
    @Override
    public long getTimeUntilReset(String identifier, String endpoint) {
        String key = buildKey(identifier, endpoint);
        
        try {
            Long ttl = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
            return (ttl != null && ttl > 0) ? ttl : 0;
        } catch (Exception e) {
            log.error("Error getting TTL for key {}: {}", key, e.getMessage());
            return 0;
        }
    }
    
    /**
     * Build Redis key from identifier and endpoint
     * Format: rate:limit:{identifier}:{endpoint}
     */
    private String buildKey(String identifier, String endpoint) {
        return KEY_PREFIX + identifier + ":" + endpoint;
    }
}
