package com.example.FoodTourApp.DTO.SecurityDTO;

import lombok.Data;

@Data
public class PinRequest {
    private String pin;
    private String currentPin; // for change PIN
}
