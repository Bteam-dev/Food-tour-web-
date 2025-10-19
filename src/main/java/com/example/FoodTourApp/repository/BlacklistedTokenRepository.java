package com.example.FoodTourApp.repository;

import com.example.FoodTourApp.entity.BlacklistedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Repository
public interface BlacklistedTokenRepository extends JpaRepository<BlacklistedToken, Integer> {

    boolean existsByToken(String token);

    @Modifying
    @Transactional
    @Query("DELETE FROM BlacklistedToken bt WHERE bt.expiresAt < :now")
    void deleteExpiredTokens(LocalDateTime now);
}

