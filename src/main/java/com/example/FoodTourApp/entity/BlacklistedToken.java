package com.example.FoodTourApp.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "blacklisted_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlacklistedToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 1000)
    private String token;

    @Column(nullable = false)
    private String email;

    @Column(name = "blacklisted_at", nullable = false)
    private LocalDateTime blacklistedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public BlacklistedToken(String token, String email, LocalDateTime expiresAt) {
        this.token = token;
        this.email = email;
        this.blacklistedAt = LocalDateTime.now();
        this.expiresAt = expiresAt;
    }
}

