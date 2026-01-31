package com.example.FoodTourApp.repository;

import com.example.FoodTourApp.entity.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Integer> {
    // Cho getWalletInfo() - lấy 10 giao dịch gần nhất
    List<WalletTransaction> findByUserIdOrderByCreatedAtDesc(Integer userId);

    // WITH PAGINATION - cho getTransactionHistory()
    Page<WalletTransaction> findByUserIdOrderByCreatedAtDesc(Integer userId, Pageable pageable);
}
