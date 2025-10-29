package com.example.FoodTourApp.repository;

import com.example.FoodTourApp.entity.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Integer> {
    List<WalletTransaction> findByUserIdOrderByCreatedAtDesc(Integer userId);
    List<WalletTransaction> findByOrderIdOrderByCreatedAtDesc(Integer orderId);
    Optional<WalletTransaction> findByExternalOrderId(String externalOrderId);
}

