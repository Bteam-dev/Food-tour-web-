package com.example.FoodTourApp.DTO.OrderDTO;

import com.example.FoodTourApp.DTO.ShopDTO.AddressRequestDTO;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Getter
@Setter
public class CreateOrderRequestDTO {

    @NotNull(message = "Delivery address ID is required")
    @Min(value = 1, message = "Delivery address ID must be positive")
    private Integer deliveryAddressId;

    @NotBlank(message = "Payment method is required")
    private String paymentMethod;

    @NotEmpty(message = "At least one cart item must be selected")
    private List<Integer> cartItemIds;

    @NotNull(message = "Delivery address is required")
    private AddressRequestDTO deliveryAddress;

    private String notes;
}
