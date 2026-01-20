package com.example.FoodTourApp.repository;

import com.example.FoodTourApp.entity.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Integer> {
    List<WalletTransaction> findByUserIdOrderByCreatedAtDesc(Integer userId);
}

