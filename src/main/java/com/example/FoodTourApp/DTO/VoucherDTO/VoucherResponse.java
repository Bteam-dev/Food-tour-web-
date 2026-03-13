package com.example.FoodTourApp.DTO.VoucherDTO;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class VoucherResponse {
    private Integer id;
    private String code;
    private String title;
    private String description;
    private String scope;
    private String discountType;
    private BigDecimal discountValue;
    private BigDecimal maxDiscountAmount;
    private BigDecimal minOrderValue;
    private Integer maxUsage;
    private Integer maxUsagePerUser;
    private Integer usedCount;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Boolean isActive;
    private Integer shopId;
    private String shopName;
    private LocalDateTime createdAt;
}

