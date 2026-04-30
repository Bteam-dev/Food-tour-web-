package com.example.FoodTourApp.repository;

import com.example.FoodTourApp.entity.FoodAnalysisMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FoodAnalysisMessageRepository extends JpaRepository<FoodAnalysisMessage, Long> {

    List<FoodAnalysisMessage> findBySession_IdOrderByCreatedAtAsc(Long sessionId);
}
