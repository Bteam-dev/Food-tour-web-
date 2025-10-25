package com.example.FoodTourApp.DTO.OrderDTO;

import com.example.FoodTourApp.DTO.ProductVariantDTO.VariantResponseDTO;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class OrderItemResponseDTO {

    private Integer id;
    private Integer productId;
    private String productName;
    private Integer quantity;
    private Double unitPrice;
    private Double totalPrice;
    private List<VariantResponseDTO> selectedVariants;
    private String specialInstructions;
}
