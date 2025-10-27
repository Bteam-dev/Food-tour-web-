package com.example.FoodTourApp.DTO.WalletDTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
    private Double amount;
    private Double balanceBefore;
    private Double balanceAfter;
    private Integer orderId;
    private String orderNumber;
    private String description;
    private LocalDateTime createdAt;
}

