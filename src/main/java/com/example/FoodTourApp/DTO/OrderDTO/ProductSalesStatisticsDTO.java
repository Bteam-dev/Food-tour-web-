package com.example.FoodTourApp.DTO.OrderDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductSalesStatisticsDTO {
    private Integer productId;
    private String productName;
    private String productImageUrl; // Ảnh đầu tiên của sản phẩm
    private Long totalQuantitySold; // Tổng số lượng bán được
    private Long totalOrders; // Số đơn hàng có sản phẩm này
    private BigDecimal totalRevenue; // Tổng doanh thu từ sản phẩm này
    private BigDecimal sellerRevenue; // Doanh thu seller nhận được (sau trừ hoa hồng)
}

