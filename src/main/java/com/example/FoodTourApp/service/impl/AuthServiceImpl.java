package com.example.FoodTourApp.service.impl;


import com.example.FoodTourApp.DTO.AuthDTO.Request.*;
import com.example.FoodTourApp.DTO.AuthDTO.Response.AuthResponse;
import com.example.FoodTourApp.DTO.UserDTO.UserResponse;
import com.example.FoodTourApp.config.JWTConfig.JwtUtils;
import com.example.FoodTourApp.entity.PasswordResetOtp;
import com.example.FoodTourApp.entity.Role;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.repository.PasswordResetOtpRepository;
import com.example.FoodTourApp.repository.RoleRepository;
import com.example.FoodTourApp.repository.UserRepository;
import com.example.FoodTourApp.service.AuthService;
import com.example.FoodTourApp.service.UserService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthServiceImpl.class);
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtil;
    private final JavaMailSender mailSender;
    private final PasswordResetOtpRepository otpRepository;
    private final AuthenticationManager authenticationManager;
    private final TokenBlacklistService tokenBlacklistService;
    private final UserService userService;

    @Value("${app.base-url}")
    private String appBaseUrl;

    public AuthServiceImpl(UserRepository userRepository, RoleRepository roleRepository,
                           PasswordEncoder passwordEncoder, JwtUtils jwtUtil, JavaMailSender mailSender,
                           PasswordResetOtpRepository otpRepository, AuthenticationManager authenticationManager,
                           TokenBlacklistService tokenBlacklistService, UserService userService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.mailSender = mailSender;
        this.otpRepository = otpRepository;
        this.authenticationManager = authenticationManager;
        this.tokenBlacklistService = tokenBlacklistService;
        this.userService = userService;
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
            String verifyLink = appBaseUrl + "/api/auth/verify-email?token=" + verifyToken;
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
    public Map<String, Object> login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        UserResponse userResponse = userService.getUserByUsername(request.getUsername());

        // Kiểm tra 2FA
        if (userService.is2FAEnabled(userResponse.getEmail())) {
            Map<String, Object> response = new HashMap<>();
            response.put("requires2FA", true);
            response.put("username", request.getUsername());
            response.put("email", userResponse.getEmail());
            response.put("message", "Vui lòng nhập mã xác thực 2FA");
            return response;
        }

        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        List<String> roles = userDetails.getAuthorities().stream()
                .map(a -> a.getAuthority()).collect(Collectors.toList());

        String accessToken = jwtUtil.generateAccessToken(userResponse.getId(), userResponse.getEmail(), roles);
        String refreshToken = jwtUtil.generateRefreshToken(userResponse.getId(), roles);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Login successful");
        response.put("user", userResponse);
        response.put("accessToken", accessToken);
        response.put("refreshToken", refreshToken);
        response.put("tokenType", "Bearer");

        logger.info("Login successful for username: {}", request.getUsername());
        return response;
    }

    @Override
    public Map<String, Object> verify2FA(String email, String code, String password) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, password));

        if (!userService.verify2FA(email, code)) {
            throw new IllegalArgumentException("Mã 2FA không hợp lệ");
        }

        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        List<String> roles = userDetails.getAuthorities().stream()
                .map(a -> a.getAuthority()).collect(Collectors.toList());

        UserResponse userResponse = userService.getUserByEmail(email);
        String accessToken = jwtUtil.generateAccessToken(userResponse.getId(), email, roles);
        String refreshToken = jwtUtil.generateRefreshToken(userResponse.getId(), roles);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "2FA verification successful");
        response.put("user", userResponse);
        response.put("accessToken", accessToken);
        response.put("refreshToken", refreshToken);
        response.put("tokenType", "Bearer");

        logger.info("2FA verification successful for email: {}", email);
        return response;
    }

    @Override
    public Map<String, Object> logout(HttpServletRequest request, String refreshToken) {
        String accessToken = jwtUtil.getJwtFromHeader(request);
        logger.info("Logout - Authorization header: {}", request.getHeader("Authorization") != null ? "Present" : "MISSING");

        if (accessToken == null) {
            accessToken = jwtUtil.getJwtFromCookies(request);
            logger.info("Logout - Cookie token: {}", accessToken != null ? "Present" : "MISSING");
        }

        logger.info("Logout - refreshToken from body: {}", refreshToken != null ? "Present" : "MISSING");

        boolean accessBlacklisted = false;
        boolean refreshBlacklisted = false;

        if (accessToken != null) {
            boolean valid = jwtUtil.validateToken(accessToken);
            logger.info("Logout - accessToken valid: {}", valid);
            if (valid) {
                Integer userId = jwtUtil.getUserIdFromToken(accessToken);
                Date exp = jwtUtil.getExpirationDateFromToken(accessToken);
                LocalDateTime expiresAt = exp.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                tokenBlacklistService.blacklistToken(accessToken, "user_" + userId, expiresAt);
                accessBlacklisted = true;
                logger.info("✅ Access token blacklisted for userId: {}", userId);
            }
        } else {
            logger.warn("⚠️ Logout called WITHOUT accessToken in header/cookie - token will NOT be blacklisted!");
        }

        if (refreshToken != null) {
            boolean valid = jwtUtil.validateToken(refreshToken);
            logger.info("Logout - refreshToken valid: {}", valid);
            if (valid) {
                Integer userId = jwtUtil.getUserIdFromToken(refreshToken);
                Date exp = jwtUtil.getExpirationDateFromToken(refreshToken);
                LocalDateTime expiresAt = exp.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                tokenBlacklistService.blacklistToken(refreshToken, "user_" + userId + "_refresh", expiresAt);
                refreshBlacklisted = true;
                logger.info("✅ Refresh token blacklisted for userId: {}", userId);
            }
        }

        // Không throw exception - logout luôn thành công
        // Dù không có token nào hợp lệ thì FE vẫn cần clear local storage
        if (!accessBlacklisted && !refreshBlacklisted) {
            logger.warn("⚠️ Logout: No token was blacklisted. FE probably didn't send Authorization header.");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Logout successful.");
        response.put("accessTokenBlacklisted", accessBlacklisted);
        response.put("refreshTokenBlacklisted", refreshBlacklisted);
        return response;
    }

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new EntityNotFoundException("Email không tồn tại trong hệ thống"));

        // Xóa các OTP cũ của email này (nếu có)
        otpRepository.deleteByEmail(request.getEmail());

        // Tạo OTP 6 số ngẫu nhiên
        String otpCode = generateOtpCode();

        // Lưu OTP vào database
        PasswordResetOtp otp = new PasswordResetOtp();
        otp.setEmail(request.getEmail());
        otp.setOtpCode(otpCode);
        otp.setCreatedAt(LocalDateTime.now());
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(5)); // OTP hết hạn sau 5 phút
        otp.setIsUsed(false);
        otpRepository.save(otp);

        // Gửi OTP qua email
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(user.getEmail());
            helper.setSubject("Mã OTP đặt lại mật khẩu");

            String emailContent = String.format(
                "<html><body>" +
                "<h2>Đặt lại mật khẩu</h2>" +
                "<p>Xin chào <strong>%s</strong>,</p>" +
                "<p>Mã OTP của bạn là: <strong style='font-size: 24px; color: #ff0000;'>%s</strong></p>" +
                "<p>Mã này có hiệu lực trong <strong>5 phút</strong>.</p>" +
                "<p>Nếu bạn không yêu cầu đặt lại mật khẩu, vui lòng bỏ qua email này.</p>" +
                "</body></html>",
                user.getFullName(), otpCode
            );

            helper.setText(emailContent, true);
            mailSender.send(message);
            logger.info("OTP sent successfully to email: {}", request.getEmail());
        } catch (MessagingException e) {
            logger.error("Failed to send OTP email: {}", e.getMessage());
            throw new RuntimeException("Không thể gửi email. Vui lòng thử lại sau.");
        }
    }

    @Override
    @Transactional
    public void verifyOtp(VerifyOtpRequest request) {
        // Tìm OTP chưa sử dụng mới nhất
        PasswordResetOtp otp = otpRepository.findByEmailAndOtpCodeAndIsUsedFalse(
                request.getEmail(), request.getOtp())
                .orElseThrow(() -> new IllegalArgumentException("Mã OTP không hợp lệ"));

        // Kiểm tra OTP đã hết hạn chưa
        if (LocalDateTime.now().isAfter(otp.getExpiresAt())) {
            throw new IllegalArgumentException("Mã OTP đã hết hạn. Vui lòng yêu cầu mã mới.");
        }

        // Đánh dấu OTP đã được verify (nhưng chưa dùng)
        otp.setVerifiedAt(LocalDateTime.now());
        otpRepository.save(otp);

        logger.info("OTP verified successfully for email: {}", request.getEmail());
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordWithOtpRequest request) {
        // Tìm OTP đã được verify và chưa sử dụng
        PasswordResetOtp otp = otpRepository.findByEmailAndOtpCodeAndIsUsedFalse(
                request.getEmail(), request.getOtp())
                .orElseThrow(() -> new IllegalArgumentException("Mã OTP không hợp lệ"));

        // Kiểm tra OTP đã được verify chưa
        if (otp.getVerifiedAt() == null) {
            throw new IllegalArgumentException("Mã OTP chưa được xác thực. Vui lòng verify OTP trước.");
        }

        // Kiểm tra OTP đã hết hạn chưa
        if (LocalDateTime.now().isAfter(otp.getExpiresAt())) {
            throw new IllegalArgumentException("Mã OTP đã hết hạn. Vui lòng yêu cầu mã mới.");
        }

        // Tìm user và reset password
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new EntityNotFoundException("User không tồn tại"));

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        // Đánh dấu OTP đã sử dụng
        otp.setIsUsed(true);
        otpRepository.save(otp);

        logger.info("Password reset successfully for email: {}", request.getEmail());
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

    /**
     * Generate random 6-digit OTP code
     */
    private String generateOtpCode() {
        Random random = new Random();
        int otp = 100000 + random.nextInt(900000); // 6 digit number
        return String.valueOf(otp);
    }
}
