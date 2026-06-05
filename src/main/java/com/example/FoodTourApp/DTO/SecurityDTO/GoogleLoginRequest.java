package com.example.FoodTourApp.DTO.SecurityDTO;

import lombok.Data;

@Data
public class GoogleLoginRequest {
    private String idToken; // Google ID token from client
}
