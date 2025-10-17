package com.example.FoodTourApp.controller;

import com.example.FoodTourApp.DTO.AuthDTO.Request.*;
import com.example.FoodTourApp.DTO.AuthDTO.Response.AuthResponse;
import com.example.FoodTourApp.service.AuthService;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import org.slf4j.Logger;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        logger.info("Received registration request for email: {}, username: {}", request.getEmail(), request.getUsername());
        try {
            AuthResponse response = authService.register(request);
            logger.info("Registration successful for email: {}", request.getEmail());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.error("Registration failed for email: {}. Error: {}", request.getEmail(), e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during registration for email: {}. Error: {}", request.getEmail(), e.getMessage(), e);
            throw e;
        }
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        logger.info("Received login request for email: {}", request.getEmail());
        try {
            AuthResponse response = authService.login(request);
            logger.info("Login successful for email: {}", request.getEmail());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.error("Login failed for email: {}. Error: {}", request.getEmail(), e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during login for email: {}. Error: {}", request.getEmail(), e.getMessage(), e);
            throw e;
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<AuthResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        logger.info("Received forgot-password request for email: {}", request.getEmail());
        try {
            authService.forgotPassword(request);
            AuthResponse response = new AuthResponse();
            response.setSuccess(true);
            response.setMessage("Email sent successfully");
            logger.info("Password reset email sent successfully for email: {}", request.getEmail());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to send password reset email for email: {}. Error: {}", request.getEmail(), e.getMessage(), e);
            throw e;
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<AuthResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        logger.info("Received POST reset-password request with token: {}", request.getToken().substring(0, Math.min(10, request.getToken().length())) + "...");
        try {
            authService.resetPassword(request);
            AuthResponse response = new AuthResponse();
            response.setSuccess(true);
            response.setMessage("Password reset successfully");
            logger.info("Password reset successful for token: {}", request.getToken().substring(0, Math.min(10, request.getToken().length())) + "...");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Password reset failed for token: {}. Error: {}", request.getToken().substring(0, Math.min(10, request.getToken().length())) + "...", e.getMessage(), e);
            throw e;
        }
    }

    @GetMapping("/reset-password")
    public ResponseEntity<?> verifyResetToken(@RequestParam("token") String token) {
        logger.info("Received GET reset-password request with token: {}", token.substring(0, Math.min(10, token.length())) + "...");
        try {
            // Xác minh token hợp lệ
            authService.validateResetToken(token);
            // Redirect tới trang reset password (hoặc trả JSON để test)
            AuthResponse response = new AuthResponse();
            response.setSuccess(true);
            response.setMessage("Reset token is valid. Please provide new password.");
            logger.info("Reset token validated successfully for token: {}", token.substring(0, Math.min(10, token.length())) + "...");
            // Tùy chọn: Redirect tới trang frontend để nhập mật khẩu mới
            // return ResponseEntity.status(302).header("Location", "http://localhost:3000/reset-password?token=" + token).build();
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Reset token validation failed for token: {}. Error: {}", token.substring(0, Math.min(10, token.length())) + "...", e.getMessage(), e);
            throw e;
        }
    }

    @PostMapping("/verify-email")
    public ResponseEntity<AuthResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        logger.info("Received POST verify-email request with token: {}", request.getToken().substring(0, Math.min(10, request.getToken().length())) + "...");
        try {
            authService.verifyEmail(request);
            AuthResponse response = new AuthResponse();
            response.setSuccess(true);
            response.setMessage("Email verified successfully");
            logger.info("Email verification successful for token: {}", request.getToken().substring(0, Math.min(10, request.getToken().length())) + "...");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Email verification failed for token: {}. Error: {}", request.getToken().substring(0, Math.min(10, request.getToken().length())) + "...", e.getMessage(), e);
            throw e;
        }
    }

    @GetMapping("/verify-email")
    public ResponseEntity<AuthResponse> verifyEmailByGet(@RequestParam("token") String token) {
        logger.info("Received GET verify-email request with token: {}", token.substring(0, Math.min(10, token.length())) + "...");
        try {
            VerifyEmailRequest request = new VerifyEmailRequest();
            request.setToken(token);
            authService.verifyEmail(request);
            AuthResponse response = new AuthResponse();
            response.setSuccess(true);
            response.setMessage("Email verified successfully. You can now log in.");
            logger.info("Email verification successful for token: {}", token.substring(0, Math.min(10, token.length())) + "...");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Email verification failed for token: {}. Error: {}", token.substring(0, Math.min(10, token.length())) + "...", e.getMessage(), e);
            throw e;
        }
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        logger.info("Received refresh-token request with token: {}", request.getRefreshToken().substring(0, Math.min(10, request.getRefreshToken().length())) + "...");
        try {
            AuthResponse response = authService.refreshToken(request);
            logger.info("Token refresh successful for token: {}", request.getRefreshToken().substring(0, Math.min(10, request.getRefreshToken().length())) + "...");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Token refresh failed for token: {}. Error: {}", request.getRefreshToken().substring(0, Math.min(10, request.getRefreshToken().length())) + "...", e.getMessage(), e);
            throw e;
        }
    }
}