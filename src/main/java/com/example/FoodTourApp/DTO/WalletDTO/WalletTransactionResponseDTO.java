package com.example.FoodTourApp.DTO.WalletDTO;

import com.fasterxml.jackson.annotation.JsonFormat;
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
public class WalletTransactionResponseDTO {

    private Integer id;
    private Integer userId;
    private String userFullName;
    private String transactionType;

    private BigDecimal amount;

    private BigDecimal balanceBefore;

    private BigDecimal balanceAfter;

    private Integer orderId;
    private String orderNumber;
    private String description;
    private LocalDateTime createdAt;
}

