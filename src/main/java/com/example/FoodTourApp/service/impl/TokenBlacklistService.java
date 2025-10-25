package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.entity.BlacklistedToken;
import com.example.FoodTourApp.repository.BlacklistedTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class TokenBlacklistService {

    private static final Logger logger = LoggerFactory.getLogger(TokenBlacklistService.class);

    private final BlacklistedTokenRepository blacklistedTokenRepository;

    public TokenBlacklistService(BlacklistedTokenRepository blacklistedTokenRepository) {
        this.blacklistedTokenRepository = blacklistedTokenRepository;
    }

    /**
     * Thêm token vào blacklist khi user logout
     */
    @Transactional
    public void blacklistToken(String token, String email, LocalDateTime expiresAt) {
        BlacklistedToken blacklistedToken = new BlacklistedToken(token, email, expiresAt);
        blacklistedTokenRepository.save(blacklistedToken);
        logger.info("Token blacklisted for email: {}", email);
    }

    /**
     * Kiểm tra xem token có bị blacklist không
     */
    public boolean isTokenBlacklisted(String token) {
        return blacklistedTokenRepository.existsByToken(token);
    }

    /**
     * Tự động xóa các token đã hết hạn mỗi 1 giờ
     * Chạy lúc 0 phút mỗi giờ để tránh đầy ổ đĩa
     */
    @Scheduled(cron = "0 0 * * * *") // Chạy mỗi giờ
    @Transactional
    public void cleanupExpiredTokens() {
        logger.info("Starting cleanup of expired blacklisted tokens...");
        try {
            LocalDateTime now = LocalDateTime.now();
            blacklistedTokenRepository.deleteExpiredTokens(now);
            logger.info("Expired tokens cleanup completed successfully");
        } catch (Exception e) {
            logger.error("Error during token cleanup: {}", e.getMessage(), e);
        }
    }
}

