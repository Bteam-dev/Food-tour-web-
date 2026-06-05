package com.example.FoodTourApp.DTO.SecurityDTO;

import lombok.Data;

@Data
public class SecurityVerifyRequest {
    private String method; // TWO_FA, FACE, PIN
    private String code;   // TOTP code or PIN
    private String verifyToken; // temporary token from login step
    // For face verification
    private String frame;
    private String framePrev;
}
