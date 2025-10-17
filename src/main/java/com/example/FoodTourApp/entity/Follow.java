package com.example.FoodTourApp.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "follows", uniqueConstraints = @UniqueConstraint(name = "unique_follow", columnNames = {"follower_id", "followable_type", "followable_id"}))
@Data
public class Follow {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "follower_id", nullable = false)
    private User follower;

    @Enumerated(EnumType.STRING)
    @Column(name = "followable_type", nullable = false)
    private FollowableType followableType;

    @Column(name = "followable_id", nullable = false)
    private Integer followableId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum FollowableType {
        user, shop
    }
}