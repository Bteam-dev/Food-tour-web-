package com.example.FoodTourApp.repository;

import com.example.FoodTourApp.entity.Order;
import com.example.FoodTourApp.entity.OrderItem;
import com.example.FoodTourApp.entity.Product;
import com.example.FoodTourApp.entity.Shop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Integer> {
    List<OrderItem> findByOrder(Order order);

    // Kiểm tra product có trong order không (cho review)
    @Query("SELECT CASE WHEN COUNT(oi) > 0 THEN true ELSE false END FROM OrderItem oi WHERE oi.order.id = :orderId AND oi.product.id = :productId")
    boolean existsByOrderIdAndProductId(@Param("orderId") Integer orderId, @Param("productId") Integer productId);

    // Thống kê sản phẩm bán chạy theo shop và khoảng thời gian
    @Query("SELECT oi.product.id as productId, oi.product.name as productName, " +
           "oi.product.imageUrls as imageUrls, " +
           "SUM(oi.quantity) as totalQuantity, " +
           "COUNT(DISTINCT oi.order.id) as totalOrders, " +
           "SUM(oi.totalPrice) as totalRevenue " +
           "FROM OrderItem oi " +
           "WHERE oi.order.shop = :shop " +
           "AND oi.order.paymentStatus = 'paid' " +
           "AND oi.order.createdAt BETWEEN :startDate AND :endDate " +
           "GROUP BY oi.product.id, oi.product.name, oi.product.imageUrls " +
           "ORDER BY SUM(oi.quantity) DESC")
    List<Object[]> findTopSellingProductsByShopAndDateRange(
            @Param("shop") Shop shop,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
}
