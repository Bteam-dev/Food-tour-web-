package com.example.FoodTourApp.repository;

import com.example.FoodTourApp.entity.SellerApproval;
import com.example.FoodTourApp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SellerApprovalRepository extends JpaRepository<SellerApproval, Integer> {
    Optional<SellerApproval> findByUserAndStatus(User user, SellerApproval.ApprovalStatus status);
    List<SellerApproval> findByStatus(SellerApproval.ApprovalStatus status);
    List<SellerApproval> findByUserId(Integer userId);
    boolean existsByUserAndStatus(User user, SellerApproval.ApprovalStatus status);
}