package com.example.FoodTourApp.repository;

import com.example.FoodTourApp.entity.FaceEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FaceEmbeddingRepository extends JpaRepository<FaceEmbedding, Long> {
    Optional<FaceEmbedding> findByUserId(Integer userId);
    boolean existsByUserId(Integer userId);
}
