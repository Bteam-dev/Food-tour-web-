package com.example.FoodTourApp.DTO.CartDTO;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class CartShopGroupDTO {

    private Integer shopId;
    private String shopName;
    private String shopLogoUrl;

    private List<CartItemResponseDTO> items;

    private Integer itemCount;          // số dòng item trong shop này
    private Integer totalQuantity;      // tổng số lượng trong shop này
    private BigDecimal shopSubtotal;    // tổng tiền của shop này (chưa ship/tax)
}
