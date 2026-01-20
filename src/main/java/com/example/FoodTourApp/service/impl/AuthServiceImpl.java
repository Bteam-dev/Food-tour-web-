package com.example.FoodTourApp.service.impl;


import com.example.FoodTourApp.DTO.AuthDTO.Request.*;
import com.example.FoodTourApp.DTO.AuthDTO.Response.AuthResponse;
import com.example.FoodTourApp.DTO.UserDTO.UserResponse;
import com.example.FoodTourApp.config.JWTConfig.JwtUtils;
import com.example.FoodTourApp.entity.Role;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.repository.RoleRepository;
import com.example.FoodTourApp.repository.UserRepository;
import com.example.FoodTourApp.service.AuthService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthServiceImpl.class);
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtil;
    private final JavaMailSender mailSender;

    public AuthServiceImpl(UserRepository userRepository, RoleRepository roleRepository,
                           PasswordEncoder passwordEncoder, JwtUtils jwtUtil, JavaMailSender mailSender) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.mailSender = mailSender;
    }

    @Override
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already exists");
        }
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (request.getPhone() != null && userRepository.findByPhone(request.getPhone()).isPresent()) {
            throw new IllegalArgumentException("Phone number already exists");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());
        user.setDateOfBirth(request.getDateOfBirth());
        user.setGender(request.getGender() != null ? User.Gender.valueOf(request.getGender()) : null);
        Role userRole = roleRepository.findByRoleName(Role.RoleName.USER)
                .orElseThrow(() -> new EntityNotFoundException("Role user not found"));
        user.setRole(userRole);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        try {
            // Dùng generateSpecialToken với userId
            String verifyToken = jwtUtil.generateSpecialToken(user.getId(), user.getEmail(), "VERIFY_EMAIL");
            String verifyLink = "http://localhost:8080/api/auth/verify-email?token=" + verifyToken;
            sendVerificationEmail(user.getEmail(), verifyLink);
        } catch (MessagingException e) {
            System.err.println("Failed to send verification email: " + e.getMessage());
        }

        UserResponse userResponse = mapToUserResponse(user);
        // Dùng userId thay vì email khi tạo token
        String accessToken = jwtUtil.generateAccessToken(
            user.getId(),
            user.getEmail(),
            java.util.List.of(user.getRole().getRoleName().toString())
        );
        String refreshToken = jwtUtil.generateRefreshToken(
            user.getId(),
            java.util.List.of(user.getRole().getRoleName().toString())  // ✅ Thêm roles
        );

        AuthResponse response = new AuthResponse();
        response.setSuccess(true);
        response.setUser(userResponse);
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setMessage("Registration successful. Please check your email to verify your account.");
        return response;
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password");
        }

         if (!user.getEmailVerified()) {
             throw new IllegalArgumentException("Email not verified. Please verify your email before logging in.");
         }

        user.setLastLogin(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        UserResponse userResponse = mapToUserResponse(user);
        // Dùng userId thay vì email khi tạo token
        String accessToken = jwtUtil.generateAccessToken(
            user.getId(),
            user.getEmail(),
            java.util.List.of(user.getRole().getRoleName().toString())
        );
        String refreshToken = jwtUtil.generateRefreshToken(
            user.getId(),
            java.util.List.of(user.getRole().getRoleName().toString())  // ✅ Thêm roles
        );

        AuthResponse response = new AuthResponse();
        response.setSuccess(true);
        response.setUser(userResponse);
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        return response;
    }

    @Override
    public void forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        // Dùng generateSpecialToken với userId thay vì email
        String resetToken = jwtUtil.generateSpecialToken(user.getId(), user.getEmail(), "RESET_PASSWORD");
        String resetLink = "http://localhost:8080/api/auth/reset-password?token=" + resetToken;

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setTo(user.getEmail());
            helper.setSubject("Password Reset Request");
            helper.setText("Click the link to reset your password: " + resetLink, true);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send email", e);
        }
    }

    @Override
    public void resetPassword(ResetPasswordRequest request) {
        // Dùng phương thức mới để lấy email và validate type
        String email = jwtUtil.getEmailFromSpecialToken(request.getToken());
        String type = jwtUtil.getTypeFromSpecialToken(request.getToken());

        if (!jwtUtil.validateToken(request.getToken()) || !"RESET_PASSWORD".equals(type)) {
            throw new IllegalArgumentException("Invalid or expired token");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    @Override
    public void validateResetToken(String token) {
        // Dùng phương thức mới để lấy email và validate type
        String email = jwtUtil.getEmailFromSpecialToken(token);
        String type = jwtUtil.getTypeFromSpecialToken(token);

        if (!jwtUtil.validateToken(token) || !"RESET_PASSWORD".equals(type)) {
            throw new IllegalArgumentException("Invalid or expired reset token");
        }

        userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
    }

    @Override
    public void verifyEmail(VerifyEmailRequest request) {
        // Dùng phương thức mới để lấy email và validate type
        String email = jwtUtil.getEmailFromSpecialToken(request.getToken());
        String type = jwtUtil.getTypeFromSpecialToken(request.getToken());

        if (!jwtUtil.validateToken(request.getToken()) || !"VERIFY_EMAIL".equals(type)) {
            throw new IllegalArgumentException("Invalid or expired verification token");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        user.setEmailVerified(true);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    @Override
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        Integer userId = jwtUtil.getUserIdFromToken(request.getRefreshToken());
        if (!jwtUtil.validateToken(request.getRefreshToken())) {
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }

        // ✅ Kiểm tra đây có phải là refresh token không
        if (!jwtUtil.isRefreshToken(request.getRefreshToken())) {
            throw new IllegalArgumentException("Token is not a refresh token");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        // ✅ Kiểm tra user có bị khóa không
        if (!user.getIsActive()) {
            throw new IllegalArgumentException("User account is inactive or blocked");
        }

        UserResponse userResponse = mapToUserResponse(user);
        // Dùng userId thay vì email khi tạo token
        String accessToken = jwtUtil.generateAccessToken(
            user.getId(),
            user.getEmail(),
            java.util.List.of(user.getRole().getRoleName().toString())
        );
        String refreshToken = jwtUtil.generateRefreshToken(
            user.getId(),
            java.util.List.of(user.getRole().getRoleName().toString())  // ✅ Thêm roles
        );

        AuthResponse response = new AuthResponse();
        response.setSuccess(true);
        response.setUser(userResponse);
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        return response;
    }

    @Override
    @Transactional
    public void logout(String token) {
        // Logout đơn giản - không cần blacklist token
        // Client sẽ xóa token ở phía frontend
        logger.info("User logged out successfully");
    }

    private void sendVerificationEmail(String email, String verifyLink) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);
        helper.setTo(email);
        helper.setSubject("Verify Your Email Address");
        helper.setText("Please click the link to verify your email: " + verifyLink, true);
        mailSender.send(message);
    }

    private UserResponse mapToUserResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setFullName(user.getFullName());
        response.setPhone(user.getPhone());
        response.setAvatarUrl(user.getAvatarUrl());
        response.setDateOfBirth(user.getDateOfBirth());
        response.setGender(user.getGender() != null ? user.getGender().toString() : null);
        response.setRoleName(user.getRole().getRoleName().toString());
        response.setIsActive(user.getIsActive());
        response.setEmailVerified(user.getEmailVerified());
        response.setLastLogin(user.getLastLogin());
        return response;
    }
}
