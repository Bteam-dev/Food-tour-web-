package com.example.FoodTourApp.DTO.WishlistDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddToWishlistRequest {
    private Integer productId;
}

