package com.example.FoodTourApp.DTO.SecurityDTO;

import lombok.Data;

@Data
public class TwoFactorVerifyRequest {
    private String code;
}
