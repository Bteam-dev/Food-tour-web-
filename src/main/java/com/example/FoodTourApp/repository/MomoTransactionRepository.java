package com.example.FoodTourApp.repository;

import com.example.FoodTourApp.entity.MomoTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MomoTransactionRepository extends JpaRepository<MomoTransaction, Integer> {
    Optional<MomoTransaction> findByOrderId(String orderId);
    Optional<MomoTransaction> findByRequestId(String requestId);
}

