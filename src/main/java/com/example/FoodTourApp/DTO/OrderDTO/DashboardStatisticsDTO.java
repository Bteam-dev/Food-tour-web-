package com.example.FoodTourApp.DTO.OrderDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatisticsDTO {
    // Tổng quan
    private Long totalOrders; // Tổng đơn hàng
    private Long pendingOrders; // Đơn chờ xử lý
    private Long completedOrders; // Đơn hoàn thành
    private Long cancelledOrders; // Đơn đã hủy

    // Doanh thu
    private BigDecimal totalRevenue; // Tổng doanh thu
    private BigDecimal platformCommission; // Tổng hoa hồng platform
    private BigDecimal sellerRevenue; // Doanh thu seller nhận được

    // Sản phẩm bán chạy (top 10)
    private List<ProductSalesStatisticsDTO> topSellingProducts;
}


