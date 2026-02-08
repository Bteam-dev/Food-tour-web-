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
}
