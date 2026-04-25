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


    @Query("""
    SELECT oi.product.id
    FROM OrderItem oi
    JOIN oi.order o
    WHERE o.user.id = :userId
    AND o.orderStatus = com.example.FoodTourApp.entity.Order.OrderStatus.delivered
    ORDER BY o.createdAt DESC
    LIMIT :limit
    """)
    List<Integer> findRecentProductIdsByUser(
            @Param("userId") Integer userId,
            @Param("limit") int limit
    );

    @Query("""
    SELECT DISTINCT oi.product.id
    FROM OrderItem oi
    WHERE oi.order.user.id = :userId
    """)
    List<Integer> findProductIdsByUser(@Param("userId") Integer userId);

    /**
     * Item-based CF: Tìm sản phẩm thường được order cùng với productId trong cùng đơn hàng.
     * Đây là co-purchase signal mạnh nhất — từ actual completed orders.
     */
    @Query("""
    SELECT oi2.product.id, COUNT(oi2.id) as co_count
    FROM OrderItem oi1
    JOIN OrderItem oi2 ON oi1.order.id = oi2.order.id
    WHERE oi1.product.id = :productId
    AND oi2.product.id != :productId
    GROUP BY oi2.product.id
    ORDER BY COUNT(oi2.id) DESC
    """)
    List<Object[]> findCoPurchasedInOrders(
            @Param("productId") Integer productId,
            org.springframework.data.domain.Pageable pageable);

    @Query("""
    SELECT p.name
    FROM OrderItem oi
    JOIN oi.order o
    JOIN oi.product p
    WHERE o.user.id = :userId
    AND o.orderStatus = com.example.FoodTourApp.entity.Order.OrderStatus.delivered
    GROUP BY p.id, p.name
    ORDER BY COUNT(oi.id) DESC
    LIMIT 1
    """)
    String findMostBoughtProductName(@Param("userId") Integer userId);
}
