package com.example.FoodTourApp.controller.PublicController;

import com.example.FoodTourApp.DTO.AuthDTO.Request.*;
import com.example.FoodTourApp.DTO.AuthDTO.Response.AuthResponse;
import com.example.FoodTourApp.config.JWTConfig.JwtUtils;
import com.example.FoodTourApp.service.AuthService;
import com.example.FoodTourApp.service.impl.TokenBlacklistService;
import com.example.FoodTourApp.service.UserService;
import com.example.FoodTourApp.DTO.UserDTO.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import org.slf4j.Logger;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Public Auth Controller - Các endpoint authentication không cần đăng nhập trước
 */
@RestController
@RequestMapping("/api/auth")
public class PublicAuthController {

    private static final Logger logger = LoggerFactory.getLogger(PublicAuthController.class);
    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final UserService userService;
    private final TokenBlacklistService tokenBlacklistService;

    public PublicAuthController(AuthService authService,
                         AuthenticationManager authenticationManager,
                         JwtUtils jwtUtils,
                         UserService userService,
                         TokenBlacklistService tokenBlacklistService) {
        this.authService = authService;
        this.authenticationManager = authenticationManager;
        this.jwtUtils = jwtUtils;
        this.userService = userService;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            // Xác thực email và password
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getEmail(), loginRequest.getPassword()));

            // Kiểm tra xem người dùng có bật 2FA không
            boolean is2FAEnabled = userService.is2FAEnabled(loginRequest.getEmail());

            if (is2FAEnabled) {
                // Nếu 2FA được bật, trả về thông báo yêu cầu mã xác thực
                Map<String, Object> responseBody = new HashMap<>();
                responseBody.put("requires2FA", true);
                responseBody.put("email", loginRequest.getEmail());
                responseBody.put("message", "Vui lòng nhập mã xác thực 2FA");

                return ResponseEntity.ok(responseBody);
            }

            // Nếu không bật 2FA, tiến hành đăng nhập
            SecurityContextHolder.getContext().setAuthentication(authentication);

            UserDetails userDetails = (UserDetails) authentication.getPrincipal();

            List<String> roles = userDetails.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList());

            // Lấy userId từ userService dựa trên email
            UserResponse userResponse = userService.getUserByEmail(userDetails.getUsername());
            Integer userId = userResponse.getId();

            // Tạo JWT token (không dùng cookie nữa, trả về trong response body)
            String accessToken = jwtUtils.generateAccessToken(userId, userDetails.getUsername(), roles);

            // Tạo response đầy đủ thông tin
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("success", true);
            responseBody.put("message", "Login successful");
            responseBody.put("user", userResponse);
            responseBody.put("accessToken", accessToken);
            responseBody.put("tokenType", "Bearer");

            logger.info("Login successful for email: {}", loginRequest.getEmail());
            return ResponseEntity.ok(responseBody);

        } catch (AuthenticationException ex) {
            logger.error("Authentication failed for email: {}. Error: {}", loginRequest.getEmail(), ex.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Authentication failed: " + ex.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
        }
    }

    // API xác thực 2FA
    @PostMapping("/verify-2fa")
    public ResponseEntity<?> verify2FA(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            String code = request.get("code");
            String password = request.get("password");

            // Xác thực mật khẩu
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password));

            // Xác thực mã 2FA
            boolean isValid = userService.verify2FA(email, code);

            if (!isValid) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Mã 2FA không hợp lệ");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            // Nếu 2FA hợp lệ, tiến hành đăng nhập
            SecurityContextHolder.getContext().setAuthentication(authentication);
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();

            List<String> roles = userDetails.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList());

            // Lấy userId từ userService
            UserResponse userResponse = userService.getUserByEmail(email);
            Integer userId = userResponse.getId();

            // Tạo JWT token
            String accessToken = jwtUtils.generateAccessToken(userId, email, roles);

            // Tạo response đầy đủ thông tin
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("success", true);
            responseBody.put("message", "2FA verification successful");
            responseBody.put("user", userResponse);
            responseBody.put("accessToken", accessToken);
            responseBody.put("tokenType", "Bearer");

            logger.info("2FA verification successful for email: {}", email);
            return ResponseEntity.ok(responseBody);

        } catch (AuthenticationException ex) {
            logger.error("2FA authentication failed. Error: {}", ex.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Authentication failed: " + ex.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {
        logger.info("Received registration request for username: {}", registerRequest.getUsername());
        try {
            if (userService.existsByUserName(registerRequest.getUsername())) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Username is already taken!");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            if (userService.existsByEmail(registerRequest.getEmail())) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Email is already in use!");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            AuthResponse authResponse = authService.register(registerRequest);

            // Tạo response đầy đủ thông tin
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("success", true);
            responseBody.put("message", authResponse.getMessage());
            responseBody.put("user", authResponse.getUser());
            responseBody.put("accessToken", authResponse.getAccessToken());
            responseBody.put("refreshToken", authResponse.getRefreshToken());

            logger.info("Registration successful for username: {}", registerRequest.getUsername());
            return ResponseEntity.ok(responseBody);
        } catch (Exception e) {
            logger.error("Registration failed for username: {}. Error: {}", registerRequest.getUsername(), e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Registration failed: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logoutUser(HttpServletRequest request, @RequestBody(required = false) Map<String, String> body) {
        try {
            // Lấy access token từ Authorization Header
            String accessToken = jwtUtils.getJwtFromHeader(request);

            if (accessToken == null) {
                // Fallback: thử lấy từ cookie nếu không có trong header
                accessToken = jwtUtils.getJwtFromCookies(request);
            }

            // Lấy refresh token từ request body (nếu có)
            String refreshToken = null;
            if (body != null && body.containsKey("refreshToken")) {
                refreshToken = body.get("refreshToken");
            }

            boolean accessTokenBlacklisted = false;
            boolean refreshTokenBlacklisted = false;

            // ✅ Blacklist access token
            if (accessToken != null && jwtUtils.validateToken(accessToken)) {
                Integer userId = jwtUtils.getUserIdFromToken(accessToken);
                Date expirationDate = jwtUtils.getExpirationDateFromToken(accessToken);
                LocalDateTime expiresAt = expirationDate.toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime();

                tokenBlacklistService.blacklistToken(accessToken, "user_" + userId, expiresAt);
                accessTokenBlacklisted = true;
                logger.info("Access token blacklisted for userId: {}", userId);
            }

            // ✅ Blacklist refresh token (nếu có)
            if (refreshToken != null && jwtUtils.validateToken(refreshToken)) {
                Integer userId = jwtUtils.getUserIdFromToken(refreshToken);
                Date expirationDate = jwtUtils.getExpirationDateFromToken(refreshToken);
                LocalDateTime expiresAt = expirationDate.toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime();

                tokenBlacklistService.blacklistToken(refreshToken, "user_" + userId + "_refresh", expiresAt);
                refreshTokenBlacklisted = true;
                logger.info("Refresh token blacklisted for userId: {}", userId);
            }

            if (accessTokenBlacklisted || refreshTokenBlacklisted) {
                Map<String, Object> responseBody = new HashMap<>();
                responseBody.put("success", true);
                responseBody.put("message", "Logout successful. Tokens have been invalidated.");
                responseBody.put("accessTokenBlacklisted", accessTokenBlacklisted);
                responseBody.put("refreshTokenBlacklisted", refreshTokenBlacklisted);

                logger.info("User logged out successfully - Access: {}, Refresh: {}",
                    accessTokenBlacklisted, refreshTokenBlacklisted);
                return ResponseEntity.ok(responseBody);
            } else {
                Map<String, Object> responseBody = new HashMap<>();
                responseBody.put("success", false);
                responseBody.put("message", "No valid token found");

                return ResponseEntity.badRequest().body(responseBody);
            }
        } catch (Exception e) {
            logger.error("Logout failed. Error: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Logout failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
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
            authService.validateResetToken(token);
            AuthResponse response = new AuthResponse();
            response.setSuccess(true);
            response.setMessage("Reset token is valid. Please provide new password.");
            logger.info("Reset token validated successfully for token: {}", token.substring(0, Math.min(10, token.length())) + "...");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Reset token validation failed for token: {}. Error: {}", token.substring(0, Math.min(10, token.length())) + "...", e.getMessage(), e);
            throw e;
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
            logger.info("Email verified successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Email verification failed. Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        logger.info("Received refresh-token request");
        try {
            AuthResponse response = authService.refreshToken(request);
            logger.info("Token refreshed successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Token refresh failed. Error: {}", e.getMessage(), e);
            throw e;
        }
    }
}

