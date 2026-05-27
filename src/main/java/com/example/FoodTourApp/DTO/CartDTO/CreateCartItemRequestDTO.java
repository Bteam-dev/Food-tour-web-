package com.example.FoodTourApp.DTO.CartDTO;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.List;
@Getter
@Setter
public class CreateCartItemRequestDTO {

    @NotNull(message = "Product ID is required")
    @Min(value = 1, message = "Product ID must be positive")
    private Integer productId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be positive")
    private Integer quantity;

    private List<Integer> variantIds;

    private String specialInstructions;
}
