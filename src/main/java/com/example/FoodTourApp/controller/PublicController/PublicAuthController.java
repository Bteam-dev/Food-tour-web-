package com.example.FoodTourApp.controller.PublicController;

import com.example.FoodTourApp.DTO.AuthDTO.Request.*;
import com.example.FoodTourApp.DTO.AuthDTO.Response.AuthResponse;
import com.example.FoodTourApp.service.AuthService;
import com.example.FoodTourApp.service.RateLimitService;
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
 * 
 * Rate Limiting applied:
 * - Login: 5 attempts per 15 minutes per email
 * - Register: 10 accounts per hour per IP
 * - Forgot Password: 3 requests per hour per email
 * - Verify OTP: 5 attempts per 5 minutes per email
 * - Send OTP: 3 requests per minute per email
 */
@RestController
@RequestMapping("/api/auth")
public class PublicAuthController {

    private static final Logger logger = LoggerFactory.getLogger(PublicAuthController.class);
    private final AuthService authService;
    private final RateLimitService rateLimitService;

    public PublicAuthController(AuthService authService, RateLimitService rateLimitService) {
        this.authService = authService;
        this.rateLimitService = rateLimitService;
    }
    
    /**
     * Get client IP address from request, considering proxy headers
     */
    private String getClientIP(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIP = request.getHeader("X-Real-IP");
        if (xRealIP != null && !xRealIP.isEmpty()) {
            return xRealIP;
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * Create rate limit error response
     */
    private ResponseEntity<?> rateLimitExceeded(String message, long timeUntilReset) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", "RATE_LIMIT_EXCEEDED");
        response.put("message", message);
        response.put("retryAfterSeconds", timeUntilReset);
        
        return ResponseEntity.status(429).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest, 
                                              HttpServletRequest request) {
        String identifier = loginRequest.getUsername(); // Use username as identifier
        
        // Rate limit: 5 attempts per 15 minutes (900 seconds)
        if (!rateLimitService.isAllowed(identifier, "login", 5, 900)) {
            long timeLeft = rateLimitService.getTimeUntilReset(identifier, "login");
            logger.warn("Rate limit exceeded for login attempt: {}", identifier);
            return rateLimitExceeded(
                "Too many login attempts. Please try again in " + (timeLeft / 60) + " minutes.", 
                timeLeft
            );
        }
        
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
    public ResponseEntity<?> verify2FA(@RequestBody Map<String, String> request, 
                                       HttpServletRequest httpRequest) {
        String email = request.get("email");
        String code = request.get("code");
        String password = request.get("password");
        
        // Rate limit: 5 attempts per 5 minutes (300 seconds)
        if (!rateLimitService.isAllowed(email, "verify-2fa", 5, 300)) {
            long timeLeft = rateLimitService.getTimeUntilReset(email, "verify-2fa");
            logger.warn("Rate limit exceeded for 2FA verification: {}", email);
            return rateLimitExceeded(
                "Too many 2FA verification attempts. Please try again in " + (timeLeft / 60) + " minutes.", 
                timeLeft
            );
        }
        
        try {
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
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest registerRequest,
                                          HttpServletRequest request) {
        logger.info("Received registration request for username: {}", registerRequest.getUsername());
        
        // Rate limit by IP: 10 registrations per hour (3600 seconds)
        String clientIP = getClientIP(request);
        if (!rateLimitService.isAllowed(clientIP, "register", 10, 3600)) {
            long timeLeft = rateLimitService.getTimeUntilReset(clientIP, "register");
            logger.warn("Rate limit exceeded for registration from IP: {}", clientIP);
            return rateLimitExceeded(
                "Too many registration attempts from this IP. Please try again in " + (timeLeft / 60) + " minutes.", 
                timeLeft
            );
        }
        
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
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                                            HttpServletRequest httpRequest) {
        logger.info("Received forgot-password request for email: {}", request.getEmail());
        
        // Rate limit: 3 requests per hour (3600 seconds) per email
        String email = request.getEmail();
        if (!rateLimitService.isAllowed(email, "forgot-password", 3, 3600)) {
            long timeLeft = rateLimitService.getTimeUntilReset(email, "forgot-password");
            logger.warn("Rate limit exceeded for forgot-password: {}", email);
            return rateLimitExceeded(
                "Too many password reset requests. Please try again in " + (timeLeft / 60) + " minutes.", 
                timeLeft
            );
        }
        
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
    public ResponseEntity<?> verifyOtp(@Valid @RequestBody VerifyOtpRequest request,
                                       HttpServletRequest httpRequest) {
        logger.info("Received verify-otp request for email: {}", request.getEmail());
        
        // Rate limit: 5 attempts per 5 minutes (300 seconds) per email
        String email = request.getEmail();
        if (!rateLimitService.isAllowed(email, "verify-otp", 5, 300)) {
            long timeLeft = rateLimitService.getTimeUntilReset(email, "verify-otp");
            logger.warn("Rate limit exceeded for verify-otp: {}", email);
            return rateLimitExceeded(
                "Too many OTP verification attempts. Please try again in " + (timeLeft / 60) + " minutes.", 
                timeLeft
            );
        }
        
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
