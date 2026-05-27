package com.example.FoodTourApp.repository;

import com.example.FoodTourApp.entity.FoodAnalysisSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FoodAnalysisSessionRepository extends JpaRepository<FoodAnalysisSession, Long> {

    Optional<FoodAnalysisSession> findBySessionKey(String sessionKey);

    /** Tìm phiên theo user + product — mỗi user × product có tối đa 1 phiên */
    Optional<FoodAnalysisSession> findByUser_IdAndProduct_Id(Integer userId, Integer productId);
}
