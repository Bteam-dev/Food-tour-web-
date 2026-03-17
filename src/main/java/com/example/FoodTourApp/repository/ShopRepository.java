package com.example.FoodTourApp.repository;

import com.example.FoodTourApp.entity.Shop;
import com.example.FoodTourApp.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShopRepository extends JpaRepository<Shop, Integer> {

    Optional<Shop> findBySellerAndId(User seller, Integer id);

    List<Shop> findBySeller(User seller);

    List<Shop> findByIsVerifiedTrue();

    Optional<Shop> findByTaxCode(String taxCode);

    // Chỉ shop đã verified VÀ đang active mới hiện ra public
    Page<Shop> findByIsVerifiedTrueAndIsActiveTrue(Pageable pageable);

    // Admin: tất cả shop (kể cả chưa verified)
    Page<Shop> findByIsActiveTrue(Pageable pageable);
}
