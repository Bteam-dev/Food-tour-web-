package com.example.FoodTourApp.DTO.WalletDTO;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // Không hiển thị fields có giá trị null
public class WalletTransactionResponseDTO {

    private Integer id;
    private Integer userId;
    private String userFullName;
    private String transactionType;
    private BigDecimal amount;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;

    // Cho giao dịch liên quan đến Order (payment, refund)
    private Integer orderId;
    private String orderNumber;

    // Cho giao dịch deposit qua MOMO
    private String momoTransactionId;  // Transaction ID từ MOMO
    private String momoOrderId;        // Order ID từ hệ thống (DEPOSIT_4_123...)

    private String description;
    private LocalDateTime createdAt;
}
