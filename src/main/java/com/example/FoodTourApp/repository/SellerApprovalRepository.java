package com.example.FoodTourApp.repository;

import com.example.FoodTourApp.entity.SellerApproval;
import com.example.FoodTourApp.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SellerApprovalRepository extends JpaRepository<SellerApproval, Integer> {
    Optional<SellerApproval> findByUserAndStatus(User user, SellerApproval.ApprovalStatus status);
    Page<SellerApproval> findByStatus(SellerApproval.ApprovalStatus status, Pageable pageable);
    Page<SellerApproval> findByUserId(Integer userId, Pageable pageable);
    boolean existsByUserAndStatus(User user, SellerApproval.ApprovalStatus status);
}