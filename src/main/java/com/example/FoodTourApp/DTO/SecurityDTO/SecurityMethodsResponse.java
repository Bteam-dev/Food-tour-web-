package com.example.FoodTourApp.DTO.SecurityDTO;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SecurityMethodsResponse {
    private boolean twoFactorEnabled;
    private boolean faceVerifyEnabled;
    private boolean paymentPinEnabled;
    private boolean emailOtpEnabled;
    private List<String> availableMethods; // TWO_FA, FACE, PIN, EMAIL_OTP
}
