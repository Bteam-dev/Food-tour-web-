package com.example.FoodTourApp.service;


import com.example.FoodTourApp.DTO.AuthDTO.Request.*;
import com.example.FoodTourApp.DTO.AuthDTO.Response.AuthResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

public interface AuthService {
    AuthResponse register(RegisterRequest request);

    /**
     * Xác thực username/password, xử lý 2FA check, tạo JWT tokens.
     * Trả về Map chứa token nếu thành công, hoặc requires2FA = true nếu cần xác thực tiếp.
     */
    Map<String, Object> login(LoginRequest request);

    /**
     * Xác thực mã 2FA + tạo JWT tokens.
     */
    Map<String, Object> verify2FA(String email, String code, String password);

    /**
     * Blacklist access token + refresh token, trả về kết quả.
     */
    Map<String, Object> logout(HttpServletRequest request, String refreshToken);

    void forgotPassword(ForgotPasswordRequest request); // Gửi OTP qua email
    void verifyOtp(VerifyOtpRequest request); // Verify OTP
    void resetPassword(ResetPasswordWithOtpRequest request); // Reset password với OTP

    void verifyEmail(VerifyEmailRequest request);
    AuthResponse refreshToken(RefreshTokenRequest request);
}
