package com.example.FoodTourApp.controller.PublicController;

import com.example.FoodTourApp.DTO.AuthDTO.Request.*;
import com.example.FoodTourApp.DTO.AuthDTO.Response.AuthResponse;
import com.example.FoodTourApp.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

/**
 * Public Auth Controller - Các endpoint authentication không cần đăng nhập trước
 */
@RestController
@RequestMapping("/api/auth")
public class PublicAuthController {

    private static final Logger logger = LoggerFactory.getLogger(PublicAuthController.class);
    private final AuthService authService;

    public PublicAuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            Map<String, Object> result = authService.login(loginRequest);
            logger.info("Login processed for username: {}", loginRequest.getUsername());
            return ResponseEntity.ok(result);
        } catch (AuthenticationException ex) {
            logger.error("Authentication failed for username: {}", loginRequest.getUsername());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    Map.of("success", false, "message", "Authentication failed: " + ex.getMessage()));
        }
    }

    // API xác thực 2FA
    @PostMapping("/verify-2fa")
    public ResponseEntity<?> verify2FA(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            String code = request.get("code");
            String password = request.get("password");
            Map<String, Object> result = authService.verify2FA(email, code, password);
            logger.info("2FA verification processed for email: {}", email);
            return ResponseEntity.ok(result);
        } catch (AuthenticationException ex) {
            logger.error("2FA authentication failed: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    Map.of("success", false, "message", "Authentication failed: " + ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", ex.getMessage()));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {
        logger.info("Received registration request for username: {}", registerRequest.getUsername());
        try {
            AuthResponse authResponse = authService.register(registerRequest);

            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("success", true);
            responseBody.put("message", authResponse.getMessage());
            responseBody.put("user", authResponse.getUser());
            responseBody.put("accessToken", authResponse.getAccessToken());
            responseBody.put("refreshToken", authResponse.getRefreshToken());

            logger.info("Registration successful for username: {}", registerRequest.getUsername());
            return ResponseEntity.ok(responseBody);
        } catch (IllegalArgumentException e) {
            logger.error("Registration validation failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            logger.error("Registration failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", "Registration failed: " + e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logoutUser(HttpServletRequest request,
                                        @RequestBody(required = false) Map<String, String> body) {
        try {
            String refreshToken = body != null ? body.get("refreshToken") : null;
            Map<String, Object> result = authService.logout(request, refreshToken);
            logger.info("User logged out successfully");
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            logger.error("Logout failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    Map.of("success", false, "message", "Logout failed: " + e.getMessage()));
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        logger.info("Received forgot-password request for email: {}", request.getEmail());
        try {
            authService.forgotPassword(request);
            return ResponseEntity.ok(Map.of("success", true,
                    "message", "Mã OTP đã được gửi đến email của bạn. Vui lòng kiểm tra email."));
        } catch (Exception e) {
            logger.error("Failed to send OTP: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        logger.info("Received verify-otp request for email: {}", request.getEmail());
        try {
            authService.verifyOtp(request);
            return ResponseEntity.ok(Map.of("success", true,
                    "message", "Mã OTP hợp lệ. Bạn có thể đặt lại mật khẩu."));
        } catch (Exception e) {
            logger.error("OTP verification failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordWithOtpRequest request) {
        logger.info("Received reset-password request for email: {}", request.getEmail());
        try {
            authService.resetPassword(request);
            return ResponseEntity.ok(Map.of("success", true,
                    "message", "Đặt lại mật khẩu thành công. Bạn có thể đăng nhập bằng mật khẩu mới."));
        } catch (Exception e) {
            logger.error("Password reset failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestParam("token") String token) {
        logger.info("Received verify-email request");
        try {
            VerifyEmailRequest request = new VerifyEmailRequest();
            request.setToken(token);
            authService.verifyEmail(request);
            AuthResponse response = new AuthResponse();
            response.setSuccess(true);
            response.setMessage("Email verified successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Email verification failed: {}", e.getMessage(), e);
            throw e;
        }
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        logger.info("Received refresh-token request");
        try {
            AuthResponse response = authService.refreshToken(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Token refresh failed: {}", e.getMessage(), e);
            throw e;
        }
    }
}
