package com.example.FoodTourApp.DTO.AuthDTO.Response;


import com.example.FoodTourApp.DTO.UserDTO.UserResponse;
import lombok.Data;

@Data
public class AuthResponse {
    private boolean success;
    private String message;
    private UserResponse user;
    private String accessToken;
    private String refreshToken;
}
