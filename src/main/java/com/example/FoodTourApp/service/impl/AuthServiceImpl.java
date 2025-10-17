package com.example.FoodTourApp.service.impl;


import com.example.FoodTourApp.DTO.AuthDTO.Request.*;
import com.example.FoodTourApp.DTO.AuthDTO.Response.AuthResponse;
import com.example.FoodTourApp.DTO.UserDTO.UserResponse;
import com.example.FoodTourApp.config.JWTConfig.JwtUtil;
import com.example.FoodTourApp.entity.Role;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.repository.RoleRepository;
import com.example.FoodTourApp.repository.UserRepository;
import com.example.FoodTourApp.service.AuthService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final JavaMailSender mailSender;

    public AuthServiceImpl(UserRepository userRepository, RoleRepository roleRepository,
                           PasswordEncoder passwordEncoder, JwtUtil jwtUtil, JavaMailSender mailSender) {
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
        Role userRole = roleRepository.findByRoleName(Role.RoleName.user)
                .orElseThrow(() -> new EntityNotFoundException("Role user not found"));
        user.setRole(userRole);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        try {
            String verifyToken = jwtUtil.generateAccessToken(user.getEmail(), "VERIFY_EMAIL");
            String verifyLink = "http://localhost:8080/api/auth/verify-email?token=" + verifyToken;
            sendVerificationEmail(user.getEmail(), verifyLink);
        } catch (MessagingException e) {
            System.err.println("Failed to send verification email: " + e.getMessage());
        }

        UserResponse userResponse = mapToUserResponse(user);
        String accessToken = jwtUtil.generateAccessToken(user.getEmail(), user.getRole().getRoleName().toString());
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());

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
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        // (Tùy chọn) Kiểm tra email đã verify chưa
        // if (!user.getEmailVerified()) {
        //     throw new IllegalArgumentException("Email not verified. Please verify your email before logging in.");
        // }

        user.setLastLogin(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        UserResponse userResponse = mapToUserResponse(user);
        String accessToken = jwtUtil.generateAccessToken(user.getEmail(), user.getRole().getRoleName().toString());
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());

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

        String resetToken = jwtUtil.generateAccessToken(user.getEmail(), "RESET_PASSWORD");
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
        String email = jwtUtil.getEmailFromToken(request.getToken());
        if (!jwtUtil.validateToken(request.getToken()) || !jwtUtil.getRoleFromToken(request.getToken()).equals("RESET_PASSWORD")) {
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
        String email = jwtUtil.getEmailFromToken(token);
        if (!jwtUtil.validateToken(token) || !jwtUtil.getRoleFromToken(token).equals("RESET_PASSWORD")) {
            throw new IllegalArgumentException("Invalid or expired reset token");
        }

        userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
    }

    @Override
    public void verifyEmail(VerifyEmailRequest request) {
        String email = jwtUtil.getEmailFromToken(request.getToken());
        if (!jwtUtil.validateToken(request.getToken()) || !jwtUtil.getRoleFromToken(request.getToken()).equals("VERIFY_EMAIL")) {
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
        String email = jwtUtil.getEmailFromToken(request.getRefreshToken());
        if (!jwtUtil.validateToken(request.getRefreshToken())) {
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        UserResponse userResponse = mapToUserResponse(user);
        String accessToken = jwtUtil.generateAccessToken(user.getEmail(), user.getRole().getRoleName().toString());
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());

        AuthResponse response = new AuthResponse();
        response.setSuccess(true);
        response.setUser(userResponse);
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        return response;
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