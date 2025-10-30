package com.example.FoodTourApp.DTO.WalletDTO;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Getter
@Setter
public class DepositRequestDTO {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1000.00", message = "Minimum deposit amount is 1000 VND")
    private BigDecimal amount;

    private String description;
}
