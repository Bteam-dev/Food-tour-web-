package com.example.FoodTourApp.DTO.OrderDTO;

import com.example.FoodTourApp.DTO.ProductVariantDTO.VariantResponseDTO;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class OrderItemResponseDTO {

    private Integer id;
    private Integer productId;
    private String productName;
    private List<String> imageUrls;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
    private List<VariantResponseDTO> selectedVariants;
    private String specialInstructions;
}
