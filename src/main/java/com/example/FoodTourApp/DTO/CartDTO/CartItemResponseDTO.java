package com.example.FoodTourApp.DTO.CartDTO;

import com.example.FoodTourApp.DTO.ProductVariantDTO.VariantResponseDTO;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class CartItemResponseDTO {

    private Integer id;
    private Integer productId;
    private String productName;
    private List<String> imageUrls;
    private BigDecimal unitPrice;       // effectivePrice + sum(priceAdjustment)
    private BigDecimal originalPrice;   // giá gốc product.price (để client biết có giảm không)
    private Integer quantity;
    private BigDecimal totalPrice;      // unitPrice * quantity
    private List<VariantResponseDTO> selectedVariants;
    private String specialInstructions;
    private LocalDateTime addedAt;
    private LocalDateTime updatedAt;
}
