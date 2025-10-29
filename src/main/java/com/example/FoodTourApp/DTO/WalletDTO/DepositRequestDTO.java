package com.example.FoodTourApp.DTO.WalletDTO;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Getter
@Setter
public class DepositRequestDTO {

    @NotNull(message = "Amount is required")
    @Min(value = 1000, message = "Minimum deposit amount is 1000 VND")
    private BigDecimal amount;

    @NotNull(message = "Payment method is required")
    private String paymentMethod; // "paypal", "vnpay", "momo", "zalopay"

    private String description;
}
