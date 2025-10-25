package com.example.FoodTourApp.DTO.CartDTO;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Min;
import java.util.List;

@Getter
@Setter
public class UpdateCartItemRequestDTO {

    @Min(value = 0, message = "Quantity cannot be negative")
    private Integer quantity;

    private List<Integer> variantIds;

    private String specialInstructions;
}
