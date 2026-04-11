package com.example.FoodTourApp.DTO.BehaviorDTO;

import com.example.FoodTourApp.entity.UserBehavior.ActionType;
import com.example.FoodTourApp.entity.UserBehavior.BehaviorSource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrackBehaviorRequest {
    private Integer productId;
    private ActionType actionType;
    private String sessionId;
    private BehaviorSource source;
    private Integer viewDurationSeconds;  // cho VIEW
    private Integer quantity;              // cho ADD_CART, PURCHASE
    private Integer rating;                // cho REVIEW
}
