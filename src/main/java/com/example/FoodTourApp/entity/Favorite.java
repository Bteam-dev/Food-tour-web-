package com.example.FoodTourApp.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "favorites", uniqueConstraints = @UniqueConstraint(name = "unique_favorite", columnNames = {"user_id", "favorable_type", "favorable_id"}))
@Data
public class Favorite {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "favorable_type", nullable = false)
    private FavorableType favorableType;

    @Column(name = "favorable_id", nullable = false)
    private Integer favorableId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum FavorableType {
        product, shop
    }
}
