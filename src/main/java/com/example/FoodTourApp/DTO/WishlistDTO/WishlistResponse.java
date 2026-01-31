package com.example.FoodTourApp.DTO.WishlistDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WishlistResponse {
    private String message;
    private boolean inWishlist;
    private Integer wishlistId;
    private long totalWishlistItems;
}

