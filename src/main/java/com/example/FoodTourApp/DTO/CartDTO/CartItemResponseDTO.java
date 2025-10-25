package com.example.FoodTourApp.DTO.CartDTO;

import com.example.FoodTourApp.DTO.ProductVariantDTO.VariantResponseDTO;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class CartItemResponseDTO {

    private Integer id;
    private Integer productId;
    private String productName;
    private Double unitPrice;
    private Integer quantity;
    private Double totalPrice;
    private List<VariantResponseDTO> selectedVariants;
    private String specialInstructions;
    private LocalDateTime addedAt;
    private LocalDateTime updatedAt;
}
