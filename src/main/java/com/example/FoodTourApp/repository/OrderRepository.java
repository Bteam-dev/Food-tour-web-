package com.example.FoodTourApp.repository;

import com.example.FoodTourApp.entity.Order;
import com.example.FoodTourApp.entity.Shop;
import com.example.FoodTourApp.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Integer> {
    // WITH PAGINATION
    Page<Order> findByUser(User user, Pageable pageable);

    // Seller lấy đơn hàng theo shop
    Page<Order> findByShop(Shop shop, Pageable pageable);

    // Lọc đơn hàng theo shop và trạng thái
    Page<Order> findByShopAndOrderStatus(Shop shop, Order.OrderStatus orderStatus, Pageable pageable);

    // Lọc đơn hàng theo shop và khoảng thời gian
    Page<Order> findByShopAndCreatedAtBetween(Shop shop, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    // Lọc đơn hàng theo shop, trạng thái và khoảng thời gian
    Page<Order> findByShopAndOrderStatusAndCreatedAtBetween(
            Shop shop, Order.OrderStatus orderStatus, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    // Đếm đơn hàng theo shop và trạng thái
    Long countByShopAndOrderStatus(Shop shop, Order.OrderStatus orderStatus);

    // Đếm đơn hàng theo shop
    Long countByShop(Shop shop);

    // Lấy đơn hàng đã thanh toán theo shop và khoảng thời gian
    @Query("SELECT o FROM Order o WHERE o.shop = :shop AND o.paymentStatus = 'paid' " +
           "AND o.createdAt BETWEEN :startDate AND :endDate")
    List<Order> findPaidOrdersByShopAndDateRange(
            @Param("shop") Shop shop,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    // Lấy đơn hàng đã thanh toán từ NHIỀU shops và khoảng thời gian
    @Query("SELECT o FROM Order o WHERE o.shop IN :shops AND o.paymentStatus = 'paid' " +
           "AND o.createdAt BETWEEN :startDate AND :endDate")
    List<Order> findPaidOrdersByShopsAndDateRange(
            @Param("shops") List<Shop> shops,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /** Đếm số lần user đã dùng voucher code này (để check max_usage_per_user) */
    long countByUserAndVoucherCode(User user, String voucherCode);

    /** Lấy đơn hàng có yêu cầu hoàn tiền (hasRefundRequest = true) theo shop */
    Page<Order> findByShopAndHasRefundRequestTrue(Shop shop, Pageable pageable);

    /** Lấy đơn hàng có yêu cầu hoàn tiền từ NHIỀU shops */
    Page<Order> findByShopInAndHasRefundRequestTrue(List<Shop> shops, Pageable pageable);
}
