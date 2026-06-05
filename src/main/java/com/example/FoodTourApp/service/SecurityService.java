package com.example.FoodTourApp.service;

import com.example.FoodTourApp.DTO.SecurityDTO.*;

import java.util.Map;

public interface SecurityService {

    // ── 2FA ──
    TwoFactorSetupResponse setup2FA(Integer userId);
    Map<String, Object> confirmEnable2FA(Integer userId, String code);
    Map<String, Object> disable2FA(Integer userId, String code);
    boolean verify2FACode(Integer userId, String code);

    // ── Payment PIN ──
    Map<String, Object> setupPin(Integer userId, String pin);
    Map<String, Object> changePin(Integer userId, String currentPin, String newPin);
    Map<String, Object> disablePin(Integer userId, String currentPin);
    boolean verifyPin(Integer userId, String pin);

    // ── Security Methods ──
    SecurityMethodsResponse getSecurityMethods(Integer userId);

    // ── Email OTP ──
    Map<String, Object> sendEmailOtp(String verifyToken);
    Map<String, Object> enableEmailOtp(Integer userId);
    Map<String, Object> disableEmailOtp(Integer userId);

    // ── Google Login ──
    Map<String, Object> googleLogin(String idToken);

    // ── Login Security Verification ──
    String createVerifyToken(Integer userId);
    Map<String, Object> verifySecurityMethod(SecurityVerifyRequest request);
}
