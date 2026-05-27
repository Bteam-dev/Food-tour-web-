package com.example.FoodTourApp.DTO.AuthDTO.Request;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class VerifyEmailRequest {
    @NotBlank(message = "Token is required")
    private String token;
}
