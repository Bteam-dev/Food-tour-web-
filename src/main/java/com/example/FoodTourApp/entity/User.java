package com.example.FoodTourApp.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "avatar_url", length = 255)
    private String avatarUrl;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender")
    private Gender gender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "email_verified", nullable = false)
    private Boolean emailVerified = false;

    @Column(name = "wallet_balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal walletBalance = BigDecimal.ZERO;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "fcm_token", length = 512)
    private String fcmToken;

    // ── Security: 2FA ──
    @Column(name = "two_factor_enabled", nullable = false)
    private Boolean twoFactorEnabled = false;

    @Column(name = "two_factor_secret", length = 512)
    private String twoFactorSecret; // encrypted TOTP secret

    // ── Security: Google OAuth ──
    @Column(name = "google_id", length = 255, unique = true)
    private String googleId;

    // ── Security: Payment PIN ──
    @Column(name = "payment_pin_hash", length = 255)
    private String paymentPinHash; // BCrypt hashed PIN

    @Column(name = "payment_pin_enabled", nullable = false)
    private Boolean paymentPinEnabled = false;

    // ── Security: Email OTP ──
    @Column(name = "email_otp_enabled", nullable = false)
    private Boolean emailOtpEnabled = false;

    public enum Gender {
        male, female, other
    }
}
