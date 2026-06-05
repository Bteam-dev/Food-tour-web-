package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.DTO.SecurityDTO.*;
import com.example.FoodTourApp.DTO.UserDTO.UserResponse;
import com.example.FoodTourApp.config.JWTConfig.JwtUtils;
import com.example.FoodTourApp.entity.FaceEmbedding;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.repository.FaceEmbeddingRepository;
import com.example.FoodTourApp.repository.UserRepository;
import com.example.FoodTourApp.service.MessageEncryptionService;
import com.example.FoodTourApp.service.SecurityService;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import dev.samstevens.totp.code.*;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.*;

@Service
public class SecurityServiceImpl implements SecurityService {

    private static final Logger log = LoggerFactory.getLogger(SecurityServiceImpl.class);
    private static final Duration VERIFY_TOKEN_TTL = Duration.ofMinutes(5);
    private static final Duration EMAIL_OTP_TTL = Duration.ofMinutes(10);

    private final UserRepository userRepository;
    private final FaceEmbeddingRepository faceEmbeddingRepository;
    private final PasswordEncoder passwordEncoder;
    private final MessageEncryptionService encryptionService;
    private final JwtUtils jwtUtils;
    private final StringRedisTemplate redis;
    private final JavaMailSender mailSender;

    @Value("${google.client-id:}")
    private String googleClientId;

    public SecurityServiceImpl(UserRepository userRepository,
                               FaceEmbeddingRepository faceEmbeddingRepository,
                               PasswordEncoder passwordEncoder,
                               MessageEncryptionService encryptionService,
                               JwtUtils jwtUtils,
                               StringRedisTemplate redis,
                               JavaMailSender mailSender) {
        this.userRepository = userRepository;
        this.faceEmbeddingRepository = faceEmbeddingRepository;
        this.passwordEncoder = passwordEncoder;
        this.encryptionService = encryptionService;
        this.jwtUtils = jwtUtils;
        this.redis = redis;
        this.mailSender = mailSender;
    }

    // ════════════════════════════════════════════════════════════════════════════
    // 2FA (TOTP - Google Authenticator)
    // ════════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public TwoFactorSetupResponse setup2FA(Integer userId) {
        User user = findUser(userId);

        if (Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
            throw new IllegalStateException("2FA đã được bật. Hãy tắt trước khi thiết lập lại.");
        }

        SecretGenerator secretGenerator = new DefaultSecretGenerator();
        String secret = secretGenerator.generate();

        // Encrypt secret before storing
        String encryptedSecret = encryptionService.encrypt(secret);
        user.setTwoFactorSecret(encryptedSecret);
        user.setTwoFactorEnabled(false); // Not enabled until verified
        userRepository.save(user);

        String qrCodeUri = buildTotpUri(secret, user.getEmail());

        log.info("[2FA] Setup initiated for userId={}", userId);
        return TwoFactorSetupResponse.builder()
                .secret(secret)
                .qrCodeUri(qrCodeUri)
                .message("Quét mã QR bằng ứng dụng Authenticator, sau đó nhập mã xác thực để hoàn tất.")
                .build();
    }

    @Override
    @Transactional
    public Map<String, Object> confirmEnable2FA(Integer userId, String code) {
        User user = findUser(userId);

        if (user.getTwoFactorSecret() == null) {
            throw new IllegalStateException("Chưa thiết lập 2FA. Hãy gọi setup trước.");
        }

        String decryptedSecret = encryptionService.decrypt(user.getTwoFactorSecret());
        if (!verifyTotpCode(decryptedSecret, code)) {
            throw new IllegalArgumentException("Mã xác thực không hợp lệ.");
        }

        user.setTwoFactorEnabled(true);
        userRepository.save(user);

        log.info("[2FA] Enabled for userId={}", userId);
        return Map.of("success", true, "message", "2FA đã được bật thành công.");
    }

    @Override
    @Transactional
    public Map<String, Object> disable2FA(Integer userId, String code) {
        User user = findUser(userId);

        if (!Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
            throw new IllegalStateException("2FA chưa được bật.");
        }

        String decryptedSecret = encryptionService.decrypt(user.getTwoFactorSecret());
        if (!verifyTotpCode(decryptedSecret, code)) {
            throw new IllegalArgumentException("Mã xác thực không hợp lệ.");
        }

        user.setTwoFactorEnabled(false);
        user.setTwoFactorSecret(null);
        userRepository.save(user);

        log.info("[2FA] Disabled for userId={}", userId);
        return Map.of("success", true, "message", "2FA đã được tắt thành công.");
    }

    @Override
    public boolean verify2FACode(Integer userId, String code) {
        User user = findUser(userId);
        if (!Boolean.TRUE.equals(user.getTwoFactorEnabled()) || user.getTwoFactorSecret() == null) {
            return false;
        }
        String decryptedSecret = encryptionService.decrypt(user.getTwoFactorSecret());
        return verifyTotpCode(decryptedSecret, code);
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Payment PIN
    // ════════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public Map<String, Object> setupPin(Integer userId, String pin) {
        User user = findUser(userId);

        if (Boolean.TRUE.equals(user.getPaymentPinEnabled())) {
            throw new IllegalStateException("Mã PIN thanh toán đã được thiết lập. Dùng đổi PIN nếu muốn thay đổi.");
        }

        validatePin(pin);
        user.setPaymentPinHash(passwordEncoder.encode(pin));
        user.setPaymentPinEnabled(true);
        userRepository.save(user);

        log.info("[PIN] Set up for userId={}", userId);
        return Map.of("success", true, "message", "Mã PIN thanh toán đã được thiết lập thành công.");
    }

    @Override
    @Transactional
    public Map<String, Object> changePin(Integer userId, String currentPin, String newPin) {
        User user = findUser(userId);

        if (!Boolean.TRUE.equals(user.getPaymentPinEnabled())) {
            throw new IllegalStateException("Chưa thiết lập mã PIN.");
        }
        if (!passwordEncoder.matches(currentPin, user.getPaymentPinHash())) {
            throw new IllegalArgumentException("Mã PIN hiện tại không đúng.");
        }

        validatePin(newPin);
        user.setPaymentPinHash(passwordEncoder.encode(newPin));
        userRepository.save(user);

        log.info("[PIN] Changed for userId={}", userId);
        return Map.of("success", true, "message", "Đổi mã PIN thành công.");
    }

    @Override
    @Transactional
    public Map<String, Object> disablePin(Integer userId, String currentPin) {
        User user = findUser(userId);

        if (!Boolean.TRUE.equals(user.getPaymentPinEnabled())) {
            throw new IllegalStateException("Mã PIN chưa được bật.");
        }
        if (!passwordEncoder.matches(currentPin, user.getPaymentPinHash())) {
            throw new IllegalArgumentException("Mã PIN không đúng.");
        }

        user.setPaymentPinEnabled(false);
        user.setPaymentPinHash(null);
        userRepository.save(user);

        log.info("[PIN] Disabled for userId={}", userId);
        return Map.of("success", true, "message", "Đã tắt mã PIN thanh toán.");
    }

    @Override
    public boolean verifyPin(Integer userId, String pin) {
        User user = findUser(userId);
        if (!Boolean.TRUE.equals(user.getPaymentPinEnabled()) || user.getPaymentPinHash() == null) {
            return true; // No PIN set, allow payment
        }
        return passwordEncoder.matches(pin, user.getPaymentPinHash());
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Security Methods
    // ════════════════════════════════════════════════════════════════════════════

    @Override
    public SecurityMethodsResponse getSecurityMethods(Integer userId) {
        User user = findUser(userId);
        boolean faceEnrolled = faceEmbeddingRepository.findByUserId(userId)
                .map(FaceEmbedding::getIsEnrolled)
                .orElse(false);

        // availableMethods = methods offered during LOGIN security verification
        // PIN is for PAYMENT only, not login
        List<String> methods = new ArrayList<>();
        if (Boolean.TRUE.equals(user.getTwoFactorEnabled())) methods.add("TWO_FA");
        if (faceEnrolled) methods.add("FACE");
        if (Boolean.TRUE.equals(user.getEmailOtpEnabled())) methods.add("EMAIL_OTP");

        return SecurityMethodsResponse.builder()
                .twoFactorEnabled(Boolean.TRUE.equals(user.getTwoFactorEnabled()))
                .faceVerifyEnabled(faceEnrolled)
                .paymentPinEnabled(Boolean.TRUE.equals(user.getPaymentPinEnabled()))
                .emailOtpEnabled(Boolean.TRUE.equals(user.getEmailOtpEnabled()))
                .availableMethods(methods)
                .build();
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Google Login
    // ════════════════════════════════════════════════════════════════════════════

    @Override
    public Map<String, Object> googleLogin(String idToken) {
        GoogleIdToken.Payload payload = verifyGoogleToken(idToken);
        if (payload == null) {
            throw new IllegalArgumentException("Google token không hợp lệ.");
        }

        String googleId = payload.getSubject();
        String email = payload.getEmail();

        // Find user by googleId first, then by email
        Optional<User> userOpt = userRepository.findByGoogleId(googleId);
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByEmail(email);
        }

        if (userOpt.isEmpty()) {
            throw new EntityNotFoundException(
                    "Không tìm thấy tài khoản với email " + email + ". Hãy đăng ký trước.");
        }

        User user = userOpt.get();

        // Link Google ID if not linked yet
        if (user.getGoogleId() == null) {
            user.setGoogleId(googleId);
            userRepository.save(user);
        }

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new IllegalStateException("Tài khoản đã bị khóa.");
        }

        // Check if user has security methods -> require verification
        SecurityMethodsResponse methods = getSecurityMethods(user.getId());
        if (!methods.getAvailableMethods().isEmpty()) {
            String verifyToken = createVerifyToken(user.getId());
            Map<String, Object> response = new HashMap<>();
            response.put("requiresVerification", true);
            response.put("verifyToken", verifyToken);
            response.put("availableMethods", methods.getAvailableMethods());
            response.put("email", user.getEmail());
            response.put("message", "Vui lòng chọn phương thức xác thực.");
            return response;
        }

        // No security methods, issue tokens directly
        return issueLoginTokens(user);
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Login Security Verification
    // ════════════════════════════════════════════════════════════════════════════

    @Override
    public String createVerifyToken(Integer userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        String redisKey = "security_verify:" + token;
        redis.opsForValue().set(redisKey, userId.toString(), VERIFY_TOKEN_TTL);
        return token;
    }

    @Override
    @Transactional
    public Map<String, Object> verifySecurityMethod(SecurityVerifyRequest request) {
        String verifyToken = request.getVerifyToken();
        String redisKey = "security_verify:" + verifyToken;
        String userIdStr = redis.opsForValue().get(redisKey);

        if (userIdStr == null) {
            throw new IllegalArgumentException("Token xác thực đã hết hạn. Vui lòng đăng nhập lại.");
        }

        Integer userId = Integer.parseInt(userIdStr);
        User user = findUser(userId);
        String method = request.getMethod();

        boolean verified = false;
        switch (method) {
            case "TWO_FA":
                if (!Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
                    throw new IllegalArgumentException("2FA chưa được bật cho tài khoản này.");
                }
                verified = verify2FACode(userId, request.getCode());
                break;
            case "FACE":
                throw new IllegalArgumentException("Xác thực khuôn mặt sử dụng endpoint /api/face/verify-security");
            case "EMAIL_OTP":
                if (!Boolean.TRUE.equals(user.getEmailOtpEnabled())) {
                    throw new IllegalArgumentException("Email OTP chưa được bật cho tài khoản này.");
                }
                String otpKey = "email_otp:" + verifyToken;
                String storedOtp = redis.opsForValue().get(otpKey);
                if (storedOtp == null) {
                    throw new IllegalArgumentException("Mã OTP đã hết hạn. Vui lòng gửi lại.");
                }
                verified = storedOtp.equals(request.getCode());
                if (verified) redis.delete(otpKey);
                break;
            default:
                throw new IllegalArgumentException("Phương thức không hợp lệ: " + method);
        }

        if (!verified) {
            throw new IllegalArgumentException("Xác thực thất bại. Mã không đúng.");
        }

        // Delete verify token after successful verification
        redis.delete(redisKey);

        log.info("[Security] Method {} verified for userId={}", method, userId);
        return issueLoginTokens(user);
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Email OTP
    // ════════════════════════════════════════════════════════════════════════════

    @Override
    public Map<String, Object> sendEmailOtp(String verifyToken) {
        String redisKey = "security_verify:" + verifyToken;
        String userIdStr = redis.opsForValue().get(redisKey);
        if (userIdStr == null) {
            throw new IllegalArgumentException("Token xác thực đã hết hạn. Vui lòng đăng nhập lại.");
        }

        User user = findUser(Integer.parseInt(userIdStr));
        if (!Boolean.TRUE.equals(user.getEmailOtpEnabled())) {
            throw new IllegalArgumentException("Email OTP chưa được bật cho tài khoản này.");
        }

        // Generate 6-digit OTP
        String otp = String.format("%06d", new Random().nextInt(1_000_000));
        redis.opsForValue().set("email_otp:" + verifyToken, otp, EMAIL_OTP_TTL);

        // Send email
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(user.getEmail());
            helper.setSubject("Mã OTP xác thực đăng nhập - FoodTourApp");
            String content = String.format(
                "<html><body>" +
                "<h2>Xác thực đăng nhập</h2>" +
                "<p>Xin chào <strong>%s</strong>,</p>" +
                "<p>Mã OTP xác thực của bạn là:</p>" +
                "<h1 style=\"letter-spacing:8px; color:#2563EB;\">%s</h1>" +
                "<p>Mã có hiệu lực trong <strong>10 phút</strong>. Không chia sẻ mã này với bất kỳ ai.</p>" +
                "<p>Nếu bạn không thực hiện đăng nhập này, hãy bỏ qua email.</p>" +
                "</body></html>",
                user.getFullName(), otp
            );
            helper.setText(content, true);
            mailSender.send(message);
            log.info("[EmailOTP] OTP sent to email of userId={}", user.getId());
        } catch (MessagingException e) {
            log.error("[EmailOTP] Failed to send email: {}", e.getMessage());
            throw new RuntimeException("Không thể gửi email. Vui lòng thử lại sau.");
        }

        // Mask email for response
        String maskedEmail = maskEmail(user.getEmail());
        return Map.of("success", true, "message", "Mã OTP đã được gửi đến " + maskedEmail);
    }

    @Override
    @Transactional
    public Map<String, Object> enableEmailOtp(Integer userId) {
        User user = findUser(userId);
        user.setEmailOtpEnabled(true);
        userRepository.save(user);
        log.info("[EmailOTP] Enabled for userId={}", userId);
        return Map.of("success", true, "message", "Xác thực Email OTP đã được bật.");
    }

    @Override
    @Transactional
    public Map<String, Object> disableEmailOtp(Integer userId) {
        User user = findUser(userId);
        user.setEmailOtpEnabled(false);
        userRepository.save(user);
        log.info("[EmailOTP] Disabled for userId={}", userId);
        return Map.of("success", true, "message", "Xác thực Email OTP đã được tắt.");
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Private helpers
    // ════════════════════════════════════════════════════════════════════════════

    private User findUser(Integer userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User không tồn tại."));
    }

    private boolean verifyTotpCode(String secret, String code) {
        TimeProvider timeProvider = new SystemTimeProvider();
        CodeGenerator codeGenerator = new DefaultCodeGenerator();
        CodeVerifier verifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
        return verifier.isValidCode(secret, code);
    }

    private String buildTotpUri(String secret, String email) {
        QrData data = new QrData.Builder()
                .label(email)
                .secret(secret)
                .issuer("FoodTourApp")
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();
        return data.getUri();
    }

    private void validatePin(String pin) {
        if (pin == null || !pin.matches("\\d{6}")) {
            throw new IllegalArgumentException("Mã PIN phải là 6 chữ số.");
        }
    }

    private GoogleIdToken.Payload verifyGoogleToken(String idTokenString) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();
            GoogleIdToken idToken = verifier.verify(idTokenString);
            return idToken != null ? idToken.getPayload() : null;
        } catch (Exception e) {
            log.error("[Google] Token verification failed: {}", e.getMessage());
            return null;
        }
    }

    private String maskEmail(String email) {
        int atIdx = email.indexOf('@');
        if (atIdx <= 2) return email;
        return email.charAt(0) + "***" + email.substring(atIdx - 1);
    }

    private Map<String, Object> issueLoginTokens(User user) {
        List<String> roles = List.of(user.getRole().getRoleName().name());
        String accessToken = jwtUtils.generateAccessToken(user.getId(), user.getEmail(), roles);
        String refreshToken = jwtUtils.generateRefreshToken(user.getId(), roles);

        UserResponse userResponse = buildUserResponse(user);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Đăng nhập thành công");
        response.put("user", userResponse);
        response.put("accessToken", accessToken);
        response.put("refreshToken", refreshToken);
        response.put("tokenType", "Bearer");
        return response;
    }

    private UserResponse buildUserResponse(User user) {
        UserResponse dto = new UserResponse();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setFullName(user.getFullName());
        dto.setPhone(user.getPhone());
        dto.setAvatarUrl(user.getAvatarUrl());
        dto.setDateOfBirth(user.getDateOfBirth());
        dto.setGender(user.getGender() != null ? user.getGender().name() : null);
        dto.setRoleName(user.getRole().getRoleName().name());
        dto.setIsActive(user.getIsActive());
        dto.setEmailVerified(user.getEmailVerified());
        dto.setLastLogin(user.getLastLogin());
        return dto;
    }
}
