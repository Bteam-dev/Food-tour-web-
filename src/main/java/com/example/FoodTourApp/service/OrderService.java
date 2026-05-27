
package com.example.FoodTourApp.service;


import com.example.FoodTourApp.DTO.OrderDTO.CreateOrderRequestDTO;
import com.example.FoodTourApp.DTO.OrderDTO.DashboardStatisticsDTO;
import com.example.FoodTourApp.DTO.OrderDTO.OrderResponseDTO;
import com.example.FoodTourApp.DTO.OrderDTO.ProductSalesStatisticsDTO;
import com.example.FoodTourApp.DTO.OrderDTO.RevenueStatisticsDTO;
import com.example.FoodTourApp.entity.Order;
import com.example.FoodTourApp.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderService {

    OrderResponseDTO createOrder(CreateOrderRequestDTO request, User user);

    // WITH PAGINATION
    Page<OrderResponseDTO> getUserOrders(User user, Pageable pageable);

    OrderResponseDTO getOrderById(Integer orderId, User user);
    void deleteOrder(Integer orderId, User user);
    OrderResponseDTO payOrder(Integer orderId, User user);

    /**
     * Seller xác nhận đã nhận tiền COD từ shipper
     */
    OrderResponseDTO confirmCODPayment(Integer orderId, User seller);

    /**
     * Seller xác nhận đơn hàng (chuyển từ pending sang confirmed)
     */
    OrderResponseDTO confirmOrder(Integer orderId, User seller);

    /**
     * Seller đánh dấu đơn hàng đã giao (chuyển sang delivered)
     * Với COD: cũng xác nhận đã nhận tiền
     */
    OrderResponseDTO markAsDelivered(Integer orderId, User seller);

    /**
     * Seller hoàn tiền cho đơn hàng
     */
    OrderResponseDTO refundOrder(Integer orderId, User seller);

    /**
     * Shop hủy đơn hàng (khi cần thiết, ví dụ user yêu cầu)
     */
    OrderResponseDTO cancelOrderBySeller(Integer orderId, User seller, String reason);

    /**
     * Seller lấy danh sách đơn hàng.
     * shopId = null → tổng hợp tất cả shops của seller
     * shopId != null → chỉ shop đó (phải thuộc seller)
     */
    Page<OrderResponseDTO> getShopOrders(User seller, Integer shopId, Pageable pageable);

    /**
     * Seller lọc đơn hàng theo trạng thái và thời gian.
     * shopId = null → tổng hợp tất cả shops
     */
    Page<OrderResponseDTO> getShopOrdersWithFilter(
            User seller,
            Integer shopId,
            Order.OrderStatus status,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable);

    /**
     * Lọc đơn hàng theo trạng thái (string) và thời gian - tự parse OrderStatus.
     * shopId = null → tổng hợp tất cả shops
     */
    Page<OrderResponseDTO> getShopOrdersWithFilterByStatusString(
            User seller,
            Integer shopId,
            String status,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable);

    /**
     * Thống kê doanh thu theo ngày/tháng/năm.
     * shopId = null → tổng hợp tất cả shops
     */
    List<RevenueStatisticsDTO> getRevenueStatistics(
            User seller,
            Integer shopId,
            String periodType, // "day", "month", "year"
            LocalDateTime startDate,
            LocalDateTime endDate);

    /**
     * Thống kê sản phẩm bán chạy.
     * shopId = null → tổng hợp tất cả shops
     */
    List<ProductSalesStatisticsDTO> getTopSellingProducts(
            User seller,
            Integer shopId,
            LocalDateTime startDate,
            LocalDateTime endDate,
            int limit);

    /**
     * Dashboard tổng quan cho seller.
     * shopId = null → tổng hợp tất cả shops
     */
    DashboardStatisticsDTO getDashboardStatistics(
            User seller,
            Integer shopId,
            LocalDateTime startDate,
            LocalDateTime endDate);

    /**
     * Lấy danh sách đơn hàng có yêu cầu hoàn tiền (hasRefundRequest = true).
     * shopId = null → tổng hợp tất cả shops
     */
    Page<OrderResponseDTO> getOrdersWithRefundRequests(User seller, Integer shopId, Pageable pageable);
}
