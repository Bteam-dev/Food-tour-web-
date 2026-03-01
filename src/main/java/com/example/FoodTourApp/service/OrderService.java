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
     * Seller lấy danh sách đơn hàng của shop với lọc
     */
    Page<OrderResponseDTO> getShopOrders(User seller, Pageable pageable);

    /**
     * Seller lọc đơn hàng theo trạng thái và thời gian
     */
    Page<OrderResponseDTO> getShopOrdersWithFilter(
            User seller,
            Order.OrderStatus status,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable);

    /**
     * Lọc đơn hàng theo trạng thái (string) và thời gian - tự parse OrderStatus.
     * Controller chỉ cần truyền string status, không cần tự parse enum.
     */
    Page<OrderResponseDTO> getShopOrdersWithFilterByStatusString(
            User seller,
            String status,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable);

    /**
     * Thống kê doanh thu theo ngày/tháng/năm
     */
    List<RevenueStatisticsDTO> getRevenueStatistics(
            User seller,
            String periodType, // "day", "month", "year"
            LocalDateTime startDate,
            LocalDateTime endDate);

    /**
     * Thống kê sản phẩm bán chạy
     */
    List<ProductSalesStatisticsDTO> getTopSellingProducts(
            User seller,
            LocalDateTime startDate,
            LocalDateTime endDate,
            int limit);

    /**
     * Dashboard tổng quan cho seller
     */
    DashboardStatisticsDTO getDashboardStatistics(
            User seller,
            LocalDateTime startDate,
            LocalDateTime endDate);

    /**
     * Lấy danh sách đơn hàng có yêu cầu hoàn tiền (hasRefundRequest = true)
     */
    Page<OrderResponseDTO> getOrdersWithRefundRequests(User seller, Pageable pageable);
}
