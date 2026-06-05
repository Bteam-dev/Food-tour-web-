package com.example.FoodTourApp.controller.UserController;

import com.example.FoodTourApp.DTO.SecurityDTO.*;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.service.SecurityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Security Management Controller - Quản lý bảo mật tài khoản
 *
 * 2FA:
 *   POST /api/user/security/2fa/setup      → Bắt đầu thiết lập 2FA (trả QR code)
 *   POST /api/user/security/2fa/confirm     → Xác nhận bật 2FA bằng mã TOTP
 *   POST /api/user/security/2fa/disable     → Tắt 2FA
 *
 * Payment PIN:
 *   POST /api/user/security/pin/setup       → Thiết lập mã PIN
 *   POST /api/user/security/pin/change      → Đổi mã PIN
 *   POST /api/user/security/pin/disable     → Tắt mã PIN
 *   POST /api/user/security/pin/verify      → Xác thực PIN (cho thanh toán)
 *
 * General:
 *   GET  /api/user/security/methods         → Lấy danh sách phương thức bảo mật đã bật
 */
@RestController
@RequestMapping("/api/user/security")
@PreAuthorize("isAuthenticated()")
public class SecurityController {

    private static final Logger log = LoggerFactory.getLogger(SecurityController.class);
    private final SecurityService securityService;

    public SecurityController(SecurityService securityService) {
        this.securityService = securityService;
    }

    private Integer getUserId(Authentication auth) {
        return ((User) auth.getPrincipal()).getId();
    }

    // ── 2FA ────────────────────────────────────────────────────────────────────

    @PostMapping("/2fa/setup")
    public ResponseEntity<?> setup2FA(Authentication auth) {
        try {
            TwoFactorSetupResponse response = securityService.setup2FA(getUserId(auth));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/2fa/confirm")
    public ResponseEntity<?> confirmEnable2FA(Authentication auth,
                                              @RequestBody TwoFactorVerifyRequest request) {
        try {
            return ResponseEntity.ok(securityService.confirmEnable2FA(getUserId(auth), request.getCode()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/2fa/disable")
    public ResponseEntity<?> disable2FA(Authentication auth,
                                        @RequestBody TwoFactorVerifyRequest request) {
        try {
            return ResponseEntity.ok(securityService.disable2FA(getUserId(auth), request.getCode()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ── Payment PIN ────────────────────────────────────────────────────────────

    @PostMapping("/pin/setup")
    public ResponseEntity<?> setupPin(Authentication auth, @RequestBody PinRequest request) {
        try {
            return ResponseEntity.ok(securityService.setupPin(getUserId(auth), request.getPin()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/pin/change")
    public ResponseEntity<?> changePin(Authentication auth, @RequestBody PinRequest request) {
        try {
            return ResponseEntity.ok(securityService.changePin(getUserId(auth), request.getCurrentPin(), request.getPin()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/pin/disable")
    public ResponseEntity<?> disablePin(Authentication auth, @RequestBody PinRequest request) {
        try {
            return ResponseEntity.ok(securityService.disablePin(getUserId(auth), request.getCurrentPin()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/pin/verify")
    public ResponseEntity<?> verifyPin(Authentication auth, @RequestBody PinRequest request) {
        boolean valid = securityService.verifyPin(getUserId(auth), request.getPin());
        if (valid) {
            return ResponseEntity.ok(Map.of("success", true, "message", "Mã PIN hợp lệ."));
        }
        return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Mã PIN không đúng."));
    }

    // ── Security Methods ───────────────────────────────────────────────────────

    @GetMapping("/methods")
    public ResponseEntity<SecurityMethodsResponse> getSecurityMethods(Authentication auth) {
        return ResponseEntity.ok(securityService.getSecurityMethods(getUserId(auth)));
    }

    // ── Email OTP ─────────────────────────────────────────────────────────────

    @PostMapping("/email-otp/enable")
    public ResponseEntity<?> enableEmailOtp(Authentication auth) {
        try {
            return ResponseEntity.ok(securityService.enableEmailOtp(getUserId(auth)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/email-otp/disable")
    public ResponseEntity<?> disableEmailOtp(Authentication auth) {
        try {
            return ResponseEntity.ok(securityService.disableEmailOtp(getUserId(auth)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}
