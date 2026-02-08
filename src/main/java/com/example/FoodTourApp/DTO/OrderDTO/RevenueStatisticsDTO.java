package com.example.FoodTourApp.DTO.OrderDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RevenueStatisticsDTO {
    private String period; // Ngày/tháng/năm (ví dụ: "2024-01-15", "2024-01", "2024")
    private Long totalOrders; // Tổng số đơn hàng
    private BigDecimal totalRevenue; // Tổng doanh thu (tổng tiền đơn hàng)
    private BigDecimal platformCommission; // Tổng hoa hồng platform
    private BigDecimal sellerRevenue; // Doanh thu thực nhận của seller (sau khi trừ hoa hồng)
}