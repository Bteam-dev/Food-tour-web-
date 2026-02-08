package com.example.FoodTourApp.DTO.OrderDTO;

import com.example.FoodTourApp.DTO.ShopDTO.AddressRequestDTO;
import lombok.Getter;
import lombok.Setter;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Getter
@Setter
public class CreateOrderRequestDTO {

    @NotBlank(message = "Payment method is required")
    private String paymentMethod;

    @NotEmpty(message = "At least one cart item must be selected")
    private List<Integer> cartItemIds;

    @NotNull(message = "Delivery address is required")
    @Valid
    private AddressRequestDTO deliveryAddress;

    private String notes;
}
