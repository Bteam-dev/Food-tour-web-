package com.example.FoodTourApp.DTO.WalletDTO;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Getter
@Setter
public class DepositRequestDTO {

    @NotNull(message = "Amount is required")
    @Min(value = 1000, message = "Minimum deposit amount is 1000 VND")
    private Double amount;

    private String description;
}
