package com.example.FoodTourApp.service;


import com.example.FoodTourApp.DTO.AuthDTO.Request.*;
import com.example.FoodTourApp.DTO.AuthDTO.Response.AuthResponse;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);

    void forgotPassword(ForgotPasswordRequest request); // Gửi OTP qua email
    void verifyOtp(VerifyOtpRequest request); // Verify OTP
    void resetPassword(ResetPasswordWithOtpRequest request); // Reset password với OTP

    void verifyEmail(VerifyEmailRequest request);
    AuthResponse refreshToken(RefreshTokenRequest request);
    void logout(String token);
}
