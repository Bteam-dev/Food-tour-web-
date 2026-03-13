package com.example.FoodTourApp.repository;

import com.example.FoodTourApp.entity.Shop;
import com.example.FoodTourApp.entity.Voucher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface VoucherRepository extends JpaRepository<Voucher, Integer> {

    Optional<Voucher> findByCodeAndIsActiveTrue(String code);

    Optional<Voucher> findByCode(String code);

    // Lấy voucher toàn sàn còn hiệu lực
    @Query("SELECT v FROM Voucher v WHERE v.scope = 'PLATFORM' AND v.isActive = true " +
           "AND v.startDate <= :now AND v.endDate >= :now")
    Page<Voucher> findActivePlatformVouchers(@Param("now") LocalDateTime now, Pageable pageable);

    // Lấy voucher của shop còn hiệu lực
    @Query("SELECT v FROM Voucher v WHERE v.shop = :shop AND v.isActive = true " +
           "AND v.startDate <= :now AND v.endDate >= :now")
    Page<Voucher> findActiveVouchersByShop(@Param("shop") Shop shop,
                                           @Param("now") LocalDateTime now,
                                           Pageable pageable);

    // Tất cả voucher của shop (admin/seller quản lý)
    Page<Voucher> findByShop(Shop shop, Pageable pageable);

    // Tất cả voucher toàn sàn (admin quản lý)
    Page<Voucher> findByScopeAndIsActiveTrue(Voucher.VoucherScope scope, Pageable pageable);
}

