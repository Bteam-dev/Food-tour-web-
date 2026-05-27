package com.example.FoodTourApp.service;

/**
 * Rate limiting service using Redis for auth-related endpoints
 * 
 * Pattern: rate:limit:{identifier}:{endpoint}
 * TTL: Based on time window (e.g., 60 seconds, 1 hour)
 */
public interface RateLimitService {
    
    /**
     * Check if request is allowed based on rate limit
     * 
     * @param identifier User identifier (userId, IP address, phone, email, etc.)
     * @param endpoint Endpoint name (e.g., "login", "register", "otp")
     * @param maxRequests Maximum requests allowed in time window
     * @param windowSeconds Time window in seconds
     * @return true if request is allowed, false if rate limit exceeded
     */
    boolean isAllowed(String identifier, String endpoint, int maxRequests, int windowSeconds);
    
    /**
     * Get remaining requests for identifier in current window
     * 
     * @param identifier User identifier
     * @param endpoint Endpoint name
     * @return Number of requests remaining (or -1 if no limit exists)
     */
    int getRemainingRequests(String identifier, String endpoint, int maxRequests);
    
    /**
     * Reset rate limit for specific identifier and endpoint
     * 
     * @param identifier User identifier
     * @param endpoint Endpoint name
     */
    void reset(String identifier, String endpoint);
    
    /**
     * Get time until rate limit resets (in seconds)
     * 
     * @param identifier User identifier
     * @param endpoint Endpoint name
     * @return Seconds until reset, or 0 if no limit active
     */
    long getTimeUntilReset(String identifier, String endpoint);
}
