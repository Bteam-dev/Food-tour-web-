package com.example.FoodTourApp.DTO.CartDTO;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class CartResponseDTO {

    private Integer cartId;
    private Integer userId;
    private List<CartShopGroupDTO> shopGroups;
    private Integer totalItems;
    private Integer totalQuantity;
    private BigDecimal grandTotal;
    private LocalDateTime updatedAt;
}
